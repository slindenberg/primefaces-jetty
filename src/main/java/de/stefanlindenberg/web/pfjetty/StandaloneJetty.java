package de.stefanlindenberg.web.pfjetty;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.LogManager;

import javax.faces.webapp.FacesServlet;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.sun.faces.config.ConfigureListener;

public final class StandaloneJetty implements UncaughtExceptionHandler {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final Server server;

	public static final String SERVER_REFERENCE = "jettyInMemory";

	public static void main(String[] args) throws Exception {
		int port = 12345;

		// FIXME remove this
		displayTrayIcon(port);

		StandaloneJetty embeddedServer = new StandaloneJetty(port);
		embeddedServer.listen();
	}

	public StandaloneJetty(int port) {

		Thread.setDefaultUncaughtExceptionHandler(this);

		// Redirect Java Util Logging, Apache Commons Logging and LOG4J to SLF4J ...
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.install();

		log.info("Starting Primefaces on Embedded Jetty ...");

		// Thread pool to handle connections ...
		QueuedThreadPool connectionThreadPool = new QueuedThreadPool();
		connectionThreadPool.setName("JETTY_CONNECTIONS");
		connectionThreadPool.setMinThreads(20);
		connectionThreadPool.setMaxThreads(500);
		connectionThreadPool.setStopTimeout(30 * 1000);

		// The server ...
		this.server = new Server(connectionThreadPool);

		try {

			// Server settings
			server.addBean(new ScheduledExecutorScheduler());
			server.setDumpAfterStart(false);
			server.setDumpBeforeStop(false);
			server.setStopAtShutdown(true);

			// HTTP Connector
			ServerConnector httpConnector = new ServerConnector(server);
			httpConnector.setHost("localhost");
			httpConnector.setPort(port);
			httpConnector.setIdleTimeout(5000);
			server.addConnector(httpConnector);
			log.info("Http connector started on port: " + port);

			// WebApp Context Handler

			final String webappDir = this.getClass().getClassLoader().getResource("webapp").toExternalForm();

			WebAppContext webappContext = new WebAppContext(webappDir, "/") {

				// Workaround to support JSF annotation scanning in Maven environment (part1)
				@Override
				public String getResourceAlias(String alias) {

					final Map<String, String> resourceAliases = (Map<String, String>) getResourceAliases();

					if (resourceAliases == null) {
						return null;
					}
					for (Entry<String, String> oneAlias : resourceAliases.entrySet()) {

						if (alias.startsWith(oneAlias.getKey())) {
							return alias.replace(oneAlias.getKey(), oneAlias.getValue());
						}
					}
					return null;
				}
			};

			// Workaround to support JSF annotation scanning in Maven environment (part2)
			try {
				webappContext.setBaseResource(new ResourceCollection(new String[] { webappDir, "./target" }));
				webappContext.setResourceAlias("/WEB-INF/classes/", "/classes/");
			} catch (Exception e) {
			}
			webappContext.setDisplayName("Primefaces 5 on Jetty Embedded 9 Example");
			webappContext.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

			// add server reference to context ...
			webappContext.setAttribute(SERVER_REFERENCE, this);

			log.info("Serving application from: " + webappDir);

			// Start JSF programmatically ...
			initializeJSF(webappContext);

			// Setup JMX
			// MBeanContainer mbContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			// server.addEventListener(mbContainer);
			// server.addBean(mbContainer);
			// server.addBean(Log.getLog());

			// Config security ...
			ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();

			// deny downloading xhtml files ..
			Constraint xhtmlConstraint = new Constraint();
			xhtmlConstraint.setName("JSF Source Code Security Constraint");
			xhtmlConstraint.setAuthenticate(true);
			xhtmlConstraint.setRoles(null);

			ConstraintMapping xhtmlConstraintMapping = new ConstraintMapping();
			xhtmlConstraintMapping.setPathSpec("*.xhtml");
			xhtmlConstraintMapping.setConstraint(xhtmlConstraint);
			securityHandler.setConstraintMappings(Collections.singletonList(xhtmlConstraintMapping));

			// Add webapp context to security handler ...
			securityHandler.setHandler(webappContext);

			// Add security handler to server ...
			server.setHandler(securityHandler);

			// Start server ...
			server.start();
			log.info("Startup completed ...");

		} catch (Exception e) {
			log.error("Unable to start server: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void listen() {
		// Listen for connections ...
		try {
			server.join();
		} catch (InterruptedException e) {
			log.error("Runntime exception in server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void initializeJSF(WebAppContext context) {
		log.info("Initializing JSF programmatically ...");

		// JSF parameters ...
		context.setInitParameter("com.sun.faces.forceLoadConfiguration", "true");
		context.setInitParameter("com.sun.faces.enableRestoreView11Compatibility", "true");

		context.setInitParameter("javax.faces.PROJECT_STAGE", "Development");
		context.setInitParameter("javax.faces.FACELETS_SKIP_COMMENTS", "true");
		context.setInitParameter("javax.faces.STATE_SAVING_METHOD", "server");
		context.setInitParameter("javax.faces.DEFAULT_SUFFIX", ".xhtml");

		context.setInitParameter("defaultHtmlEscape", "true");

		context.setInitParameter("primefaces.THEME", "redmond");
		context.setInitParameter("primefaces.CLIENT_SIDE_VALIDATION", "false");

		// Add JSF Listener for initialization ...
		context.addEventListener(new ConfigureListener());

		// JSF Servlet ...
		ServletHolder jsfServlet = new ServletHolder(FacesServlet.class);
		jsfServlet.setDisplayName("Faces Servlet");
		jsfServlet.setName("Faces_Servlet");
		jsfServlet.setInitOrder(0);

		// Add to web context ...
		context.addServlet(jsfServlet, "*.jsf");
		context.setWelcomeFiles(new String[] { "index.jsf" });
	}

	private static void displayTrayIcon(final int port) throws Exception {
		if (!GraphicsEnvironment.isHeadless()) {
			final TrayIcon trayIcon = new TrayIcon(new ImageIcon(StandaloneJetty.class.getResource("/pf.png")).getImage());
			trayIcon.setImageAutoSize(true);
			trayIcon.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent ev) {
					try {
						Desktop.getDesktop().browse(new URI("http://localhost:" + port));
					} catch (Exception e) {
					}
				}
			});
			PopupMenu popup = new PopupMenu();
			MenuItem browseAction = new MenuItem("Browse");
			browseAction.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						Desktop.getDesktop().browse(new URI("http://localhost:" + port));
					} catch (Exception ex) {
					}
				}
			});
			MenuItem quitAction = new MenuItem("Quit");
			quitAction.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					System.exit(0);
				}
			});

			popup.add(browseAction);
			popup.add(quitAction);
			trayIcon.setPopupMenu(popup);
			SystemTray.getSystemTray().add(trayIcon);
			trayIcon.displayMessage("Jetty Embedded Server (http://localhost:" + port + ")", "Click this icon to open the browser.", TrayIcon.MessageType.INFO);
		}
	}

	public void shutdown() {
		log.info("Stopping server ...");
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(3000);
					for (Handler handler : server.getHandlers()) {
						handler.stop();
					}
					server.stop();
					server.getThreadPool().join();
					System.exit(0);
				} catch (Exception ex) {
					System.out.println("Failed to stop Jetty");
				}
			}
		}.start();
	}

	@Override
	public void uncaughtException(Thread thread, Throwable e) {
		log.error("Uncaught exception:" + e.getMessage(), e);
		if (!GraphicsEnvironment.isHeadless()) {
			String message = "[" + e.getClass() + "] " + e.getMessage();
			JOptionPane.showMessageDialog(null, message, "An uncaught error occured!", JOptionPane.ERROR_MESSAGE);
		}
	}
}
