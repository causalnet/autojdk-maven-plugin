package au.net.causal.maven.plugins.autojdk;

import io.foojay.api.discoclient.DiscoClient;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

public class FoojayJdkRepository implements JdkArchiveRepository<FoojayArtifact>
{
    private final DiscoClient discoClient;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final FileDownloader fileDownloader;

    public FoojayJdkRepository(DiscoClient discoClient, RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession,
                               FileDownloader fileDownloader)
    {
        this.discoClient = Objects.requireNonNull(discoClient);
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.repositorySystemSession = Objects.requireNonNull(repositorySystemSession);
        this.fileDownloader = Objects.requireNonNull(fileDownloader);
    }

    @Override
    public Collection<? extends FoojayArtifact> search(JdkSearchRequest searchRequest) throws JdkRepositoryException
    {
        //TODO
        return null;
    }

    @Override
    public JdkArchive resolveArchive(FoojayArtifact jdkArtifact) throws JdkRepositoryException
    {
        //First check if we don't already have a cached version in the local repo
        Artifact mavenArtifact = mavenArtifactForJdkArtifact(jdkArtifact);
        ArtifactRequest lookupRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList() /*no remotes, local only*/, null);
        try
        {
            ArtifactResult lookupResult = repositorySystem.resolveArtifact(repositorySystemSession, lookupRequest);

            //If we get here, archive was found in the local repo so just return that
            return new JdkArchive(jdkArtifact, lookupResult.getArtifact().getFile());
        }
        catch (ArtifactResolutionException e)
        {
            //Not found in local repo, that's fine, move on to downloading it
        }

        //If we get here, could not find in the local repo so download it and save to local repo
        String downloadUri = discoClient.getPkgDirectDownloadUri(jdkArtifact.getFoojayPkg().getId());

        //Download into local repository

        //First download to temp file
        try (FileDownloader.Download download = fileDownloader.downloadFile(new URL(downloadUri)))
        {
            //Once file is downloaded, install to local repo
            Path downloadedFile = download.getFile();

            //download's close() in try-with-resources will handle deleting the temp file

            InstallRequest request = new InstallRequest();
            mavenArtifact = mavenArtifact.setFile(downloadedFile.toFile());
            request.setArtifacts(Collections.singletonList(mavenArtifact));
            try
            {
                repositorySystem.install(repositorySystemSession, request);

                //Re-resolve to local repo so file of artifact gets filled in
                mavenArtifact = mavenArtifact.setFile(null);
                ArtifactRequest reresolveRequest = new ArtifactRequest(mavenArtifact, Collections.emptyList(), null);
                ArtifactResult reresolveResult = repositorySystem.resolveArtifact(repositorySystemSession, reresolveRequest);
                File jdkArchiveInLocalRepo = reresolveResult.getArtifact().getFile();

                return new JdkArchive(jdkArtifact, jdkArchiveInLocalRepo);
            }
            catch (InstallationException | ArtifactResolutionException e)
            {
                throw new JdkRepositoryException("Failed to install JDK archive artifact to local repo: " + e.getMessage(), e);
            }
        }
        catch (MalformedURLException e)
        {
            throw new JdkRepositoryException("Invalid JDK download URL: " + downloadUri + " - " + e, e);
        }
        catch (IOException e)
        {
            throw new JdkRepositoryException("Failed to download JDK: " + e.getMessage(), e);
        }
    }

    protected Artifact mavenArtifactForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return new DefaultArtifact("au.net.causal.autojdk.jdk",
                                   mavenArtifactIdForJdkArtifact(jdkArtifact),
                                   mavenClassifierForJdkArtifact(jdkArtifact),
                                   jdkArtifact.getArchiveType().getFileExtension(),
                                   jdkArtifact.getVersion());
    }

    private String mavenArtifactIdForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return jdkArtifact.getVendor().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String mavenClassifierForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return jdkArtifact.getOperatingSystem().name().toLowerCase(Locale.ROOT) + "_" + jdkArtifact.getArchitecture().name().toLowerCase(Locale.ROOT);
    }

    private String mavenExtensionForJdkArtifact(JdkArtifact jdkArtifact)
    {
        return jdkArtifact.getArchiveType().name();
    }
}
