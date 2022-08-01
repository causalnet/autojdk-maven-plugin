package au.net.causal.maven.plugins.autojdk;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.util.Objects;

public class MavenProxySelector implements SimpleFileDownloader.ProxySelector
{
    private final RepositorySystemSession repoSession;

    public MavenProxySelector(RepositorySystemSession repoSession)
    {
        this.repoSession = Objects.requireNonNull(repoSession);
    }

    @Override
    public Proxy selectProxy(URL url)
    {
        ProxySelector proxySelector = repoSession.getProxySelector();
        RemoteRepository tempRepo = new RemoteRepository.Builder(null, null, url.toExternalForm()).build();
        org.eclipse.aether.repository.Proxy mavenProxy = proxySelector.getProxy(tempRepo);

        if (mavenProxy == null)
            return null;

        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(mavenProxy.getHost(), mavenProxy.getPort()));
    }

    @Override
    public Authenticator proxyAuthenticator(URL url)
    {
        ProxySelector proxySelector = repoSession.getProxySelector();
        RemoteRepository tempRepo = new RemoteRepository.Builder(null, null, url.toExternalForm()).build();
        org.eclipse.aether.repository.Proxy mavenProxy = proxySelector.getProxy(tempRepo);

        if (mavenProxy != null && mavenProxy.getAuthentication() != null)
        {
            tempRepo = new RemoteRepository.Builder(tempRepo).setProxy(mavenProxy).build();
            AuthenticationContext authCtx = AuthenticationContext.forProxy(repoSession, tempRepo);
            if (authCtx != null)
            {
                try
                {
                    String username = authCtx.get(AuthenticationContext.USERNAME);
                    String password = authCtx.get(AuthenticationContext.PASSWORD);

                    //NTLM is not supported according to the Maven doco despite having reference in the source code
                    //so just ignore for now
                    //p.setNtlmDomain(authCtx.get(AuthenticationContext.NTLM_DOMAIN));
                    //p.setNtlmHost(authCtx.get(AuthenticationContext.NTLM_WORKSTATION));

                    if (username != null && password != null)
                    {
                        return new Authenticator()
                        {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication()
                            {
                                if (getRequestorType() == RequestorType.PROXY)
                                    return new PasswordAuthentication(username, password.toCharArray());
                                else
                                    return null;
                            }
                        };
                    }
                }
                finally
                {
                    authCtx.close();
                }
            }
        }


        return null;
    }
}
