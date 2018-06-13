/******************************************************************************
 * Copyright (c) 2013, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.git.build.hjplugin.http;

import hudson.model.TaskListener;
import hudson.util.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.InvalidCredentialsException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;

import com.ibm.team.git.build.hjplugin.Messages;
import com.ibm.team.git.build.hjplugin.RtcJsonUtil;

/**
 * Collection of methods to handle authentication and providing the response
 * back. For a GET, the caller supplies URI and gets JSON back. For a PUT, the
 * caller supplies URI &amp; any stream data.
 */
public class HttpUtils {

	private static final String SLASH = "/"; //$NON-NLS-1$

	private static final String TEXT_JSON = "text/json"; //$NON-NLS-1$

	private static final String UTF_8 = "UTF-8"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger(HttpUtils.class
			.getName());

	private static final String NEW_LINE = System.getProperty("line.separator"); //$NON-NLS-1$

	// aka "http.conn-manager.timeout"
	// Its how long we should wait to get a connection from the connection
	// manager
	private static final int CONNECTION_REQUEST_TIMEOUT = 480000;

	private static final String FORM_AUTHREQUIRED_HEADER = "X-com-ibm-team-repository-web-auth-msg"; //$NON-NLS-1$
	private static final String FORM_AUTHREQUIRED_HEADER_VALUE = "authrequired"; //$NON-NLS-1$
	private static final String AUTHFAILED_HEADER_VALUE = "authfailed"; //$NON-NLS-1$
	private static final String BASIC_AUTHREQUIRED_HEADER = "WWW-Authenticate"; //$NON-NLS-1$
	private static Pattern JAUTH_PATTERN = Pattern
			.compile("^[Jj][Aa][Uu][Tt][Hh]\\s+.*"); //$NON-NLS-1$
	private static Pattern BASIC_PATTERN = Pattern
			.compile("^[Bb][Aa][Ss][Ii][Cc]\\s+.*"); //$NON-NLS-1$

	private static CloseableHttpClient HTTP_CLIENT = null;

	private static SSLConnectionSocketFactory SSL_CONNECTION_SOCKET_FACTORY;

	// static {
	// LOGGER.setLevel(Level.FINER);
	// ConsoleHandler handler = new ConsoleHandler();
	// handler.setLevel(Level.FINER);
	// LOGGER.addHandler(handler);
	// }

	public static class RtcHttpResult {
		private HttpClientContext httpContext;
		private JSON json;

		RtcHttpResult() {
			this.httpContext = null;
			this.json = null;
		}

		RtcHttpResult(HttpClientContext httpContext, JSON json) {
			this.httpContext = httpContext;
			this.json = json;
		}

		/**
		 * @return The context used in the request. May be used for subsequent
		 *         rest requests relating to the same user
		 */
		public HttpClientContext getHttpContext() {
			return httpContext;
		}

		/**
		 * @return JSON response from the get request
		 */
		public JSON getJson() {
			return json;
		}

		public String getResultAsString() {
			if (json != null) {
				return RtcJsonUtil.getReturnValue(json);
			}
			return null;
		}
		
		public String[] getResultAsStringArray() {
			if (json != null) {
				return RtcJsonUtil.getReturnValues(json);
			}
			return null;
		}

		public boolean getResultAsBoolean() {
			if (json != null) {
				return Boolean.valueOf(RtcJsonUtil.getReturnValue(json));
			}
			return false;
		}
	}

	public static RtcHttpResult performPost(String serverURI, String uri,
			String userId, String password, int timeout,
			List<NameValuePair> params, TaskListener listener, HttpClientContext httpContext)
			throws IOException, InvalidCredentialsException,
			GeneralSecurityException {

		String fullURI = getFullURI(serverURI, uri);
		HttpPost request = getPOSTWithJson(fullURI, timeout);
		
		request.setEntity(new UrlEncodedFormEntity(params));
		CloseableHttpClient httpClient = getClient();
		
		if(httpContext == null) {
			httpContext = createHttpContext();
		}
		
		LOGGER.info("POST: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request,
				httpContext);
		try {
			// based on the response do any authentication. If authentication is
			// performed
			// that means this response doesn't contain the result and the
			// request needs to
			// be re-issued.
			boolean authenticationPerformed = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);
			if (authenticationPerformed) {
				// retry post
				request = getPOSTWithJson(fullURI, timeout);
				request.setEntity(new UrlEncodedFormEntity(params));
				response = httpClient.execute(request, httpContext);
			}
			int statusCode = response.getStatusLine().getStatusCode();
			
			if (statusCode == 200) {
				InputStreamReader inputStream = new InputStreamReader(response
						.getEntity().getContent(), UTF_8);
				try {
					String responseContent = IOUtils.toString(inputStream);
					JSON resultJson = JSONSerializer.toJSON(responseContent);
					return new RtcHttpResult(httpContext, resultJson);
				} finally {
					try {
						inputStream.close();
					} catch (IOException e) {
						LOGGER.log(Level.WARNING, 
								String.format("Failed to close the result input stream for request: %s", fullURI) //$NON-NLS-1$
								, e); 
					}
				}
			} else if (statusCode == 401) {
				// if still un-authorized, then there is a good chance the basic
				// credentials are bad.
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				// capture details about the error
				LOGGER.warning(Messages.HttpUtils_POST_failed(fullURI, statusCode));
				if (listener != null) {
					listener.fatalError(Messages.HttpUtils_POST_failed(fullURI, statusCode));
				}
				throw logError(fullURI, response,
						Messages.HttpUtils_POST_failed(fullURI, statusCode));
			}
		} finally {
			closeResponse(response);
		}

	}

	/**
	 * Perform GET request against an RTC server
	 * 
	 * @param serverURI
	 *            The RTC server
	 * @param uri
	 *            The relative URI for the GET. It is expected it is already
	 *            encoded if necessary.
	 * @param userId
	 *            The userId to authenticate as
	 * @param password
	 *            The password to authenticate with
	 * @param timeout
	 *            The timeout period for the connection (in seconds)
	 * @param httpContext
	 *            The context from the login if cycle is being managed by the
	 *            caller Otherwise <code>null</code> and this call will handle
	 *            the login.
	 * @param listener
	 *            The listener to report errors to. May be <code>null</code>
	 * @return Result of the GET (JSON response)
	 * @throws IOException
	 *             Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException
	 */
	public static RtcHttpResult performGet(String serverURI, String uri,
			String userId, String password, int timeout,
			HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException,
			GeneralSecurityException {

		CloseableHttpClient httpClient = getClient();
		String fullURI = getFullURI(serverURI, uri);
		HttpGet request = getGET(fullURI, timeout);
		if (httpContext == null) {
			httpContext = createHttpContext();
		}

		LOGGER.finer("GET: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request,
				httpContext);
		try {
			// based on the response do any authentication. If authentication is
			// performed
			// that means this response doesn't contain the result and the
			// request needs to
			// be re-issued.
			boolean authenticationPerformed = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);
			if (authenticationPerformed) {
				// retry get
				request = getGET(fullURI, timeout);
				response = httpClient.execute(request, httpContext);
			}
			int statusCode = response.getStatusLine().getStatusCode();
			if (LOGGER.isLoggable(Level.INFO)) {
				LOGGER.info("Status code " + statusCode + " for URI "+ fullURI); //$NON-NLS-1$ //$NON-NLS-2$
			}

			if (statusCode == 200) {
				InputStreamReader inputStream = new InputStreamReader(response
						.getEntity().getContent(), UTF_8);
				try {
					String responseContent = IOUtils.toString(inputStream);
					LOGGER.info("Response " + responseContent + " for URI "+ fullURI); //$NON-NLS-1$ //$NON-NLS-2$
					RtcHttpResult result = new RtcHttpResult(httpContext,
							JSONSerializer.toJSON(responseContent));
					return result;
				} finally {
					try {
						inputStream.close();
					} catch (IOException e) {
						LOGGER.log(Level.WARNING, 
								String.format("Failed to close the result input stream for request: %s", fullURI) //$NON-NLS-1$
								, e);
					}
				}
			} else if (statusCode == 401) {
				// if still un-authorized, then there is a good chance the basic
				// credentials are bad.
				throw new InvalidCredentialsException(Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				// capture details about the error
				LOGGER.warning(Messages.HttpUtils_GET_failed(fullURI, statusCode));
				if (listener != null) {
					listener.fatalError(Messages.HttpUtils_GET_failed(fullURI, statusCode));
				}
				throw logError(fullURI, response,
						Messages.HttpUtils_GET_failed(fullURI, statusCode));
			}
		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Perform PUT request against an RTC server
	 * 
	 * @param serverURI
	 *            The RTC server
	 * @param uri
	 *            The relative URI for the PUT. It is expected that it is
	 *            already encoded if necessary.
	 * @param userId
	 *            The userId to authenticate as
	 * @param password
	 *            The password to authenticate with
	 * @param timeout
	 *            The timeout period for the connection (in seconds)
	 * @param json
	 *            The JSON object to put to the RTC server
	 * @param httpContext
	 *            The context from the login if cycle is being managed by the
	 *            caller Otherwise <code>null</code> and this call will handle
	 *            the login.
	 * @param listener
	 *            The listener to report errors to. May be <code>null</code> if
	 *            there is no listener.
	 * @return The HttpContext for the request. May be reused in subsequent
	 *         requests for the same user
	 * @throws IOException
	 *             Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 * @throws GeneralSecurityException
	 */
	public static HttpClientContext performPut(String serverURI, String uri,
			String userId, String password, int timeout, final JSONObject json,
			HttpClientContext httpContext, TaskListener listener)
			throws IOException, InvalidCredentialsException,
			GeneralSecurityException {

		CloseableHttpClient httpClient = getClient();
		// How to fill the request body (Clone doesn't work)
		ContentProducer cp = new ContentProducer() {
			public void writeTo(OutputStream outstream) throws IOException {
				Writer writer = new OutputStreamWriter(outstream, UTF_8);
				json.write(writer);
				writer.flush();
			}
		};
		HttpEntity entity = new EntityTemplate(cp);
		String fullURI = getFullURI(serverURI, uri);
		HttpPut put = getPUT(fullURI, timeout);
		put.setEntity(entity);

		if (httpContext == null) {
			httpContext = createHttpContext();
		}

		LOGGER.finer("PUT: " + put.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(put, httpContext);
		try {
			// based on the response do any authentication. If authentication is
			// performed
			// that means the put was not successful and the request needs to be
			// re-issued.
			boolean authenticationPerformed = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, listener);

			// retry put request if we had to do authentication
			if (authenticationPerformed) {
				put = getPUT(fullURI, timeout);
				put.setEntity(entity);
				response = httpClient.execute(put, httpContext);
			}

			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 401) {
				// It is an unusual case to get here (in our current work flow)
				// because it means
				// the user has become unauthenticated since the previous
				// request
				// (i.e. the get of the value about to put back)
				throw new InvalidCredentialsException(
						Messages.HttpUtils_authentication_failed(userId, serverURI));

			} else {
				int responseClass = statusCode / 100;
				if (responseClass != 2) {
					if (listener != null) {
						listener.fatalError(Messages.HttpUtils_PUT_failed(fullURI, statusCode));
					}

					throw logError(fullURI, response,
							Messages.HttpUtils_PUT_failed(fullURI, statusCode));
				}
				return httpContext;
			}
		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Only used to test connection.
	 * 
	 * @param serverURI
	 *            The RTC server
	 * @param userId
	 *            The userId to authenticate as
	 * @param password
	 *            The password to authenticate with
	 * @param timeout
	 *            The timeout period for the connection (in seconds)
	 * @throws IOException
	 *             Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 *             if authentication fails
	 * @throws GeneralSecurityException
	 */
	public static void validateCredentials(String serverURI, String userId,
			String password, int timeout, HttpClientContext httpContext) throws IOException,
			GeneralSecurityException, InvalidCredentialsException {
		// We can't directly do a post because we need a JSession id cookie.
		// Instead attempt to do a get and then verify the credentials when we
		// need to do the form based auth. Don't bother to re-issue the get though. We just
		// want to know if the Login works
		CloseableHttpClient httpClient = getClient();
		HttpGet request = getGET(serverURI, timeout);
		if(httpContext == null) {
			httpContext = createHttpContext();
		}
		
		LOGGER.finer("GET: " + request.getURI()); //$NON-NLS-1$
		CloseableHttpResponse response = httpClient.execute(request,
				httpContext);
		try {
			boolean authenticationPerformed = authenticateIfRequired(response,
					httpClient, httpContext, serverURI, userId, password,
					timeout, null);
			if (authenticationPerformed) {
				// retry get - if doing form based auth, not required
				// but if its basic auth then we do need to re-issue since basic
				// just updates the context
				request = getGET(serverURI, timeout);
				response = httpClient.execute(request, httpContext);
				if (response.getStatusLine().getStatusCode() == 401) {
					// still not authorized
					throw new InvalidCredentialsException(
							Messages.HttpUtils_authentication_failed(userId, serverURI));
				}
			}

		} finally {
			closeResponse(response);
		}
	}

	/**
	 * Log the error that occurred and provide an exception that encapsulates
	 * the failure as best as possible. This means parsing the output and if its
	 * from RTC extract the stack trace from there.
	 * 
	 * @param fullURI
	 *            The URI requested
	 * @param httpResponse
	 *            The response from the request
	 * @param message
	 *            A message for the failure if nothing can be detected from the
	 *            response
	 * @return An exception representing the failure
	 */
	@SuppressWarnings("rawtypes")
	private static IOException logError(String fullURI,
			CloseableHttpResponse httpResponse, String message) {
		printMessageHeaders(httpResponse);

		IOException error = new IOException(message);
		try {
			InputStreamReader inputStream = new InputStreamReader(httpResponse
					.getEntity().getContent(), UTF_8);
			try {
				String response = IOUtils.toString(inputStream);
				// this is one lonnnng string if its a stack trace.
				// try to get it as JSON so we can output it in a more friendly
				// way.
				try {
					JSON json = JSONSerializer.toJSON(response);
					response = json.toString(4);
					if (json instanceof JSONObject) {
						// see if we have a stack trace
						JSONObject jsonObject = (JSONObject) json;
						String errorMessage = jsonObject
								.getString("errorMessage"); //$NON-NLS-1$
						error = new IOException(errorMessage);
						JSONArray trace = jsonObject
								.getJSONArray("errorTraceMarshall"); //$NON-NLS-1$
						List<StackTraceElement> stackElements = new ArrayList<StackTraceElement>(
								trace.size());
						for (Iterator iterator = trace.iterator(); iterator
								.hasNext();) {
							Object element = iterator.next();
							if (element instanceof JSONObject) {
								JSONObject jsonElement = (JSONObject) element;
								String cls = jsonElement
										.getString("errorTraceClassName"); //$NON-NLS-1$
								String method = jsonElement
										.getString("errorTraceMethodName"); //$NON-NLS-1$
								String file = jsonElement
										.getString("errorTraceFileName"); //$NON-NLS-1$
								int line = jsonElement
										.getInt("errorTraceLineNumber"); //$NON-NLS-1$
								StackTraceElement stackElement = new StackTraceElement(
										cls, method, file, line);
								stackElements.add(stackElement);
							}
						}
						error.setStackTrace(stackElements
								.toArray(new StackTraceElement[stackElements
										.size()]));

						// our RTC responses have the stack trace in there
						// twice. Remove 1 copy of it.
						jsonObject.remove("errorTraceMarshall"); //$NON-NLS-1$
						response = jsonObject.toString(4);
					}
				} catch (JSONException e) {
					// not JSON or not a RTC stack trace in the JSONObject so
					// just log what we have
				}
				LOGGER.finer(response);
			} finally {
				try {
					inputStream.close();
				} catch (IOException e) {
					LOGGER.finer("Failed to close the result input stream for request: " + fullURI); //$NON-NLS-1$
				}
			}
		} catch (IOException e) {
			LOGGER.finer("Unable to capture details of the failure"); //$NON-NLS-1$
		}
		return error;
	}

	/**
	 * Because the creation of the SSLConnectionSocketFactory is expensive
	 * (reads from disk the certs file) we will cache the client. The client is
	 * re-used so doesn't have any target RTC server info associated with it.
	 * 
	 * @return an HttpClient
	 * @throws GeneralSecurityException
	 */
	public synchronized static CloseableHttpClient getClient()
			throws GeneralSecurityException {
		if (HTTP_CLIENT == null) {
			HttpClientBuilder clientBuilder = HttpClientBuilder.create();

			clientBuilder.setSSLSocketFactory(getSSLConnectionSocketFactory());

			// TODO to find out if this is sufficient, we can periodically dump
			// the PoolStats
			clientBuilder.setMaxConnPerRoute(10);
			clientBuilder.setMaxConnTotal(100);

			// do not allow redirects on POST
			//clientBuilder.setRedirectStrategy(new DefaultRedirectStrategy());
			clientBuilder.setRedirectStrategy(new LaxRedirectStrategy());

			// How long to wait to get a connection from our connection manager.
			// The default
			// timeouts are forever which is probably not good.
			// TODO If we set it when creating the GET, PUT & POST maybe its not
			// really needed here
			RequestConfig config = getRequestConfig(CONNECTION_REQUEST_TIMEOUT);
			clientBuilder.setDefaultRequestConfig(config);

			// This will create a client with the defaults (which includes the
			// PoolingConnectionManager)
			HTTP_CLIENT = clientBuilder.build();
		}

		return HTTP_CLIENT;
	}
	
	private static synchronized SSLConnectionSocketFactory getSSLConnectionSocketFactory()
			throws GeneralSecurityException {
		if (SSL_CONNECTION_SOCKET_FACTORY == null) {

			/** Create a trust manager that does not validate certificate chains */
			TrustManager trustManager = new X509TrustManager() {
				public void checkClientTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
					/** Ignore Method Call */
				}

				public void checkServerTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
					/** Ignore Method Call */
				}

				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			SSLContext sslContext = SSLContextUtil
					.createSSLContext(trustManager);
			SSL_CONNECTION_SOCKET_FACTORY = new SSLConnectionSocketFactory(
					sslContext, new X509HostnameVerifier() {

						public boolean verify(String arg0, SSLSession arg1) {
							return true;
						}

						public void verify(String host, SSLSocket ssl)
								throws IOException {
							// Not implemented
						}

						public void verify(String host, X509Certificate cert)
								throws SSLException {
							// Not implemented
						}

						public void verify(String host, String[] cns,
								String[] subjectAlts) throws SSLException {
							// Not implemented
						}
					});
		}
		return SSL_CONNECTION_SOCKET_FACTORY;
	}

	/**
	 * Obtain a GET request to execute
	 * 
	 * @param fullURI
	 *            The full uri
	 * @param timeout
	 *            The time out period in seconds
	 * @return a GET http request
	 */
	private static HttpGet getGET(String fullURI, int timeout) {
		HttpGet get = new HttpGet(fullURI);
		get.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		get.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		get.setConfig(getRequestConfig(timeout));
		return get;
	}

	/**
	 * Obtain a PUT request to execute
	 * 
	 * @param fullURI
	 *            The full uri
	 * @param timeout
	 *            The time out period in seconds
	 * @return a PUT http request
	 */
	private static HttpPut getPUT(String fullURI, int timeout) {
		HttpPut put = new HttpPut(fullURI);
		put.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		put.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		put.addHeader("Content-type", TEXT_JSON); //$NON-NLS-1$
		put.setConfig(getRequestConfig(timeout));

		return put;
	}

	/**
	 * Obtain a POST request to execute
	 * 
	 * @param fullURI
	 *            The full uri
	 * @param timeout
	 *            The time out period in seconds
	 * @return a POST http request
	 */
	private static HttpPost getPOST(String fullURI, int timeout) {
		HttpPost post = new HttpPost(fullURI);
		post.setConfig(getRequestConfig(timeout));
		return post;
	}
	
	private static HttpPost getPOSTWithJson(String fullURI, int timeout) {
		HttpPost post = new HttpPost(fullURI);
		post.setHeader("Accept-Charset", UTF_8); //$NON-NLS-1$
		post.addHeader("Accept", TEXT_JSON); //$NON-NLS-1$
		post.setConfig(getRequestConfig(timeout));
		return post;
	}


	/**
	 * Get the request configuration tailored to the timeout period for
	 * accessing the RTC server.
	 * 
	 * @param timeout
	 *            The timout period in seconds
	 * @return The request configuration
	 */
	private static RequestConfig getRequestConfig(int timeout) {
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(timeout * 1000)
				.setConnectTimeout(timeout * 1000)
				.setConnectionRequestTimeout(timeout * 1000)
				.setCircularRedirectsAllowed(true).build();
		return requestConfig;
	}

	private static String getFullURI(String serverURI, String relativeURI) {
		String fullURI = serverURI;
		if (!serverURI.endsWith(SLASH) && !relativeURI.startsWith(SLASH)) {
			fullURI = serverURI + SLASH + relativeURI;
		} else {
			fullURI = serverURI + relativeURI;
		}
		return fullURI;
	}

	/**
	 * Creates and returns a new HttpContext with a new cookie store, for use
	 * with the singleton HTTP_CLIENT.
	 * 
	 * @return a new HttpContext
	 */
	public static HttpClientContext createHttpContext() {
		CookieStore cookieStore = new BasicCookieStore();
		HttpClientContext httpContext = new HttpClientContext();
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
		return httpContext;
	}

	/**
	 * Perform any authentication required (Form or Basic) if the previous
	 * request did not succeed.
	 * 
	 * @param response
	 *            The response from the previous request. It will be consumed if
	 *            we are going to do authentication.
	 * @param httpClient
	 *            The httpClient to use for authentication
	 * @param httpContext
	 *            The current context for use in request. Required.
	 * @param serverURI
	 *            The full URI for the server
	 * @param userId
	 *            The user that is performing the request
	 * @param password
	 *            The user's password
	 * @param timeout
	 *            The timeout for the password
	 * @param listener
	 *            The listener should any messages need to be logged. May be
	 *            <code>null</code>
	 * @return <code>true</code> if authentication was required. Request should
	 *         be re-tried. <code>false</code> if authentication was not
	 *         required. The response still contains the result.
	 * @throws InvalidCredentialsException
	 *             Thrown if the user's userid or password are invalid.
	 * @throws IOException
	 *             Thrown if anything else goes wrong.
	 */
	private static boolean authenticateIfRequired(
			CloseableHttpResponse response, CloseableHttpClient httpClient,
			HttpClientContext httpContext, String serverURI, String userId,
			String password, int timeout, TaskListener listener)
			throws InvalidCredentialsException, IOException {

		// decide what kind of Auth is required if any
		int statusCode = response.getStatusLine().getStatusCode();
		Header formHeader = response.getFirstHeader(FORM_AUTHREQUIRED_HEADER);
		Header basicHeader = response.getFirstHeader(BASIC_AUTHREQUIRED_HEADER);
		if (formHeader != null
				&& FORM_AUTHREQUIRED_HEADER_VALUE.equals(formHeader.getValue())) {
			closeResponse(response);

			// login using Form based auth
			 handleFormBasedChallenge(httpClient, httpContext, serverURI,
					userId, password, timeout, listener);
			 return true;
		} else if (statusCode == 401 && basicHeader != null) {
			if (JAUTH_PATTERN.matcher(basicHeader.getValue()).matches()) {
				throw new UnsupportedOperationException();

			} else if (BASIC_PATTERN.matcher(basicHeader.getValue()).matches()) {
				closeResponse(response);

				// setup the context to use Basic auth
				handleBasicAuthChallenge(httpContext, serverURI, userId,
						password, listener);
				return true;
			}

		}
		return false;
	}

	/**
	 * Post a login form to the server when authentication was required by the
	 * previous request
	 * 
	 * @param httpClient
	 *            The httpClient to use for the requests
	 * @param httpContext
	 *            httpContext with it's own cookie store for use with the
	 *            singleton HTTP_CLIENT Not <code>null</code>
	 * @param serverURI
	 *            The RTC server
	 * @param userId
	 *            The userId to authenticate as
	 * @param password
	 *            The password to authenticate with
	 * @param timeout
	 *            The timeout period for the connection (in seconds)
	 * @param listener
	 *            The listener to report errors to. May be <code>null</code>
	 * @throws IOException
	 *             Thrown if things go wrong
	 * @throws InvalidCredentialsException
	 *             if authentication fails
	 */
	private static void handleFormBasedChallenge(
			CloseableHttpClient httpClient, HttpClientContext httpContext,
			String serverURI, String userId, String password, int timeout,
			TaskListener listener) throws IOException,
			InvalidCredentialsException {

		// The server requires an authentication: Create the login form
		String fullURI = getFullURI(serverURI, "/j_security_check"); //$NON-NLS-1$
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		nvps.add(new BasicNameValuePair("j_username", userId)); //$NON-NLS-1$
		nvps.add(new BasicNameValuePair("j_password", password)); //$NON-NLS-1$

		HttpPost formPost = getPOST(fullURI, timeout);
		formPost.setEntity(new UrlEncodedFormEntity(nvps, UTF_8));

		// The client submits the login form
		LOGGER.info("POST: " + formPost.getURI()); //$NON-NLS-1$
		//HttpClientContext authContext = createHttpContext();
		CloseableHttpResponse formResponse = httpClient.execute(formPost,
				httpContext);
		try {

			int statusCode = formResponse.getStatusLine().getStatusCode();
			Header header = formResponse
					.getFirstHeader(FORM_AUTHREQUIRED_HEADER);
			if(statusCode == 302) {
				//do nothing
			}else if (statusCode / 100 == 2 && (header != null)
					&& (AUTHFAILED_HEADER_VALUE.equals(header.getValue()))) {
				// The login failed
				throw new InvalidCredentialsException(
						Messages.HttpUtils_authentication_failed(userId, serverURI));
			} else if (statusCode / 100 != 2) {
				if (listener != null) {
					listener.fatalError(Messages.HttpUtils_LOGIN_failed(userId, fullURI, statusCode));
				}

				throw logError(fullURI, formResponse,
						Messages.HttpUtils_LOGIN_failed(userId, fullURI, statusCode));
			}
		} finally {
			closeResponse(formResponse);
		}
		return;
	}

	/**
	 * For Basic auth, configure the context to do pre-emptive auth with the
	 * credentials given.
	 * 
	 * @param httpContext
	 *            The context in use for the requests.
	 * @param serverURI
	 *            The RTC server we will be authenticating against
	 * @param userId
	 *            The userId to login with
	 * @param password
	 *            The password for the User
	 * @param listener
	 *            A listen to log messages to, Not required
	 * @throws IOException
	 *             Thrown if anything goes wrong
	 */
	private static void handleBasicAuthChallenge(HttpClientContext httpContext,
			String serverURI, String userId, String password,
			TaskListener listener) throws IOException {

		URI uri;
		try {
			uri = new URI(serverURI);
		} catch (URISyntaxException e) {
			throw new IOException(
					Messages.HttpUtils_invalid_server(serverURI), e);
		}
		HttpHost target = new HttpHost(uri.getHost(), uri.getPort(),
				uri.getScheme());
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
				new AuthScope(target.getHostName(), target.getPort()),
				new UsernamePasswordCredentials(userId, password));
		httpContext.setAttribute(HttpClientContext.CREDS_PROVIDER,
				credsProvider);

		// Create AuthCache instance
		AuthCache authCache = new BasicAuthCache();
		// Generate BASIC scheme object and add it to the local auth cache
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(target, basicAuth);

		// Add AuthCache to the execution context
		httpContext.setAuthCache(authCache);
	}

	private static void closeResponse(CloseableHttpResponse formResponse) {
		try {
			formResponse.close();
		} catch (IOException e) {
			// we don't care we are logged in or are throwing an exception for a
			// different problem
			LOGGER.log(Level.FINER, "Failed to close response", e); //$NON-NLS-1$
		}
	}

	/**
	 * Print the HTTP request - for debugging purposes
	 */
	@SuppressWarnings("unused")
	private static void printRequest(HttpRequestBase request) {
		if (LOGGER.isLoggable(Level.FINER)) {
			StringBuffer logMessage = new StringBuffer();
			logMessage.append(NEW_LINE)
					.append("\t- Method: ").append(request.getMethod()); //$NON-NLS-1$ //
			logMessage.append(NEW_LINE)
					.append("\t- URL: ").append(request.getURI()); //$NON-NLS-1$
			logMessage.append(NEW_LINE).append("\t- Headers: "); //$NON-NLS-1$
			Header[] headers = request.getAllHeaders();
			for (int i = 0; i < headers.length; i++) {
				logMessage
						.append(NEW_LINE)
						.append("\t\t- ").append(headers[i].getName()).append(": ").append(headers[i].getValue()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			LOGGER.finer(logMessage.toString());
		}
	}

	/**
	 * Print out the HTTPMessage headers - for debugging purposes
	 */
	private static void printMessageHeaders(HttpMessage message) {
		if (LOGGER.isLoggable(Level.FINER)) {
			StringBuffer logMessage = new StringBuffer("Message Headers:"); //$NON-NLS-1$
			Header[] headers = message.getAllHeaders();
			for (int i = 0; i < headers.length; i++) {
				logMessage
						.append(NEW_LINE)
						.append("\t- ").append(headers[i].getName()).append(": ").append(headers[i].getValue()); //$NON-NLS-1$ //$NON-NLS-2$
			}
			LOGGER.finer(logMessage.toString());
		}
	}

	/**
	 * Print out the HTTP Response body - for debugging purposes
	 */
	@SuppressWarnings("unused")
	private static void printResponseBody(CloseableHttpResponse response) {
		if (LOGGER.isLoggable(Level.FINER)) {
			HttpEntity entity = response.getEntity();
			if (entity == null)
				return;

			StringBuffer logMessage = new StringBuffer("Response Body:"); //$NON-NLS-1$
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new InputStreamReader(
						entity.getContent()));
				String line = reader.readLine();
				while (line != null) {
					logMessage.append(NEW_LINE).append(line);
					line = reader.readLine();
				}
				LOGGER.finer(logMessage.toString());
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException e) {
						// ignore
					}
				}
			}
		}
	}

	@SuppressWarnings("unused")
	private static void printCookies(HttpClientContext httpContext) {
		if (LOGGER.isLoggable(Level.FINER)) {
			LOGGER.finer("Cookies:"); //$NON-NLS-1$
			CookieStore cookieStore = (CookieStore) httpContext
					.getAttribute(HttpClientContext.COOKIE_STORE);
			List<Cookie> cookies = cookieStore.getCookies();
			if (cookies.isEmpty()) {
				System.out.println("\tNone"); //$NON-NLS-1$
			} else {
				for (int i = 0; i < cookies.size(); i++) {
					LOGGER.finer("\t- " + cookies.get(i).toString()); //$NON-NLS-1$
				}
			}
		}
	}
}
