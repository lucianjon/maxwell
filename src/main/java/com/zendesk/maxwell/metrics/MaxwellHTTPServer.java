package com.zendesk.maxwell.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.PingServlet;
import com.zendesk.maxwell.MaxwellContext;
import com.zendesk.maxwell.util.StoppableTask;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.util.concurrent.TimeoutException;


public class MaxwellHTTPServer {
	public MaxwellHTTPServer(int port, MetricRegistry metricRegistry, HealthCheckRegistry healthCheckRegistry, MaxwellContext context) {
		MaxwellHTTPServerWorker maxwellHTTPServerWorker = new MaxwellHTTPServerWorker(port, metricRegistry, healthCheckRegistry);
		Thread thread = new Thread(maxwellHTTPServerWorker);

		context.addTask(maxwellHTTPServerWorker);
		thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				e.printStackTrace();
				System.exit(1);
			}
		});

		thread.setDaemon(true);
		thread.start();
	}
}

class MaxwellHTTPServerWorker implements StoppableTask, Runnable {

	private int port;
	private MetricRegistry metricRegistry;
	private HealthCheckRegistry healthCheckRegistry;
	private CollectorRegistry collectorRegistry;
	private Server server;

	public MaxwellHTTPServerWorker(int port, MetricRegistry metricRegistry, HealthCheckRegistry healthCheckRegistry) {
		this.port = port;
		this.metricRegistry = metricRegistry;
		this.healthCheckRegistry = healthCheckRegistry;
		this.collectorRegistry = new CollectorRegistry();

		collectorRegistry.register(new DropwizardExports(metricRegistry));
	}

	public void startServer() throws Exception {
		this.server = new Server(this.port);

		ServletContextHandler handler = new ServletContextHandler(this.server, "/");
		// TODO: there is a way to wire these up automagically via the AdminServlet, but it escapes me right now
		handler.addServlet(new ServletHolder(new MetricsServlet(this.metricRegistry)), "/metrics");
		handler.addServlet(new ServletHolder(new io.prometheus.client.exporter.MetricsServlet(this.collectorRegistry)), "/prometheus_metrics");
		handler.addServlet(new ServletHolder(new HealthCheckServlet(this.healthCheckRegistry)), "/healthcheck");
		handler.addServlet(new ServletHolder(new PingServlet()), "/ping");

		this.server.start();
		this.server.join();
	}

	@Override
	public void run() {
		try {
			startServer();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void requestStop() {
		try {
			this.server.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void awaitStop(Long timeout) throws TimeoutException {
	}
}
