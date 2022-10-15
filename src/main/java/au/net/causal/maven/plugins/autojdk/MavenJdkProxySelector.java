package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;

public class MavenJdkProxySelector extends ProxySelector
{
    private static final Logger log = LoggerFactory.getLogger(MavenJdkProxySelector.class);

    private final RepositorySystemSession repoSession;

    public MavenJdkProxySelector(RepositorySystemSession repoSession)
    {
        this.repoSession = Objects.requireNonNull(repoSession);
    }

    protected org.eclipse.aether.repository.Proxy mavenProxyForUri(URI uri)
    {
        org.eclipse.aether.repository.ProxySelector proxySelector = repoSession.getProxySelector();
        RemoteRepository tempRepo = new RemoteRepository.Builder(null, null, uri.toString()).build();
        return proxySelector.getProxy(tempRepo);
    }

    @Override
    public List<Proxy> select(URI uri)
    {
        org.eclipse.aether.repository.Proxy mavenProxy = mavenProxyForUri(uri);

        if (mavenProxy == null)
        {
            log.debug("No proxy for URI: " + uri);
            return List.of(Proxy.NO_PROXY);
        }

        log.debug("Using proxy for URI: " + uri + " ---> " + mavenProxy.getHost() + ":" + mavenProxy.getPort());

        return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(mavenProxy.getHost(), mavenProxy.getPort())));
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe)
    {
        //Ignore failure?
        log.warn("Proxy connect failure (" + sa + "): " + uri);
    }

    public Authenticator authenticator()
    {
        return new MavenProxyAuthenticator();
    }

    public class MavenProxyAuthenticator extends Authenticator
    {
        @Override
        protected PasswordAuthentication getPasswordAuthentication()
        {
            if (getRequestorType() != RequestorType.PROXY)
                return null;

            URL url = getRequestingURL();

            log.debug("Getting credentials for proxy for URL: " + url);

            org.eclipse.aether.repository.ProxySelector proxySelector = repoSession.getProxySelector();
            RemoteRepository tempRepo = new RemoteRepository.Builder(null, null, url.toExternalForm()).build();
            org.eclipse.aether.repository.Proxy mavenProxy = proxySelector.getProxy(tempRepo);

            if (mavenProxy == null || mavenProxy.getAuthentication() == null)
                return null;

            tempRepo = new RemoteRepository.Builder(tempRepo).setProxy(mavenProxy).build();
            AuthenticationContext authCtx = AuthenticationContext.forProxy(repoSession, tempRepo);

            if (authCtx == null)
                return null; //No credentials

            try
            {
                String username = authCtx.get(AuthenticationContext.USERNAME);
                String password = authCtx.get(AuthenticationContext.PASSWORD);

                //NTLM is not supported according to the Maven doco despite having reference in the source code
                //so just ignore for now
                //p.setNtlmDomain(authCtx.get(AuthenticationContext.NTLM_DOMAIN));
                //p.setNtlmHost(authCtx.get(AuthenticationContext.NTLM_WORKSTATION));

                if (username != null && password != null)
                    return new PasswordAuthentication(username, password.toCharArray());
                else
                    return null;
            }
            finally
            {
                authCtx.close();
            }
        }
    }
}
