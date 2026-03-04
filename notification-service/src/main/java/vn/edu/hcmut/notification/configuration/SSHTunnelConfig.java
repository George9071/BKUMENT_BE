package vn.edu.hcmut.notification.configuration;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue = "true")
public class SSHTunnelConfig {
    @Value("${ssh.tunnel.host}")
    private String host;

    @Value("${ssh.tunnel.port:22}")
    private int port;

    @Value("${ssh.tunnel.user}")
    private String user;

    @Value("${ssh.tunnel.password}")
    private String password;

    @Value("${ssh.tunnel.local-port}")
    private int localPort;

    @Value("${ssh.tunnel.remote-port}")
    private int remotePort;

    private Session session;

    @PostConstruct
    public void buildSshTunnel() {
        try {
            log.info("Start setting up an SSH tunnel to {}...", host);
            JSch jsch = new JSch();

            session = jsch.getSession(user, host, port);
            session.setPassword(password);

            // Disable SSH key confirmation prompts. (for the code to run automatically on FEs.)
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            // Initiate SSH connection
            session.connect();

            // Port Forwarding from local directly to MongoDB on the server.
            session.setPortForwardingL(localPort, "127.0.0.1", remotePort);

            log.info("Success! SSH tunnel has been opened at localhost:{}", localPort);
        } catch (Exception e) {
            log.error("An error has occurred when open SSH Tunnel: {}", e.getMessage());
            throw new RuntimeException("Unable to establish an SSH tunnel connection to " + host, e);
        }
    }

    @PreDestroy
    public void closeSshTunnel() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("Closed SSH Tunnel safely.");
        }
    }
}
