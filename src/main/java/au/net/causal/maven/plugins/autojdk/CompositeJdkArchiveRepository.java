package au.net.causal.maven.plugins.autojdk;

import eu.hansolo.jdktools.Architecture;
import eu.hansolo.jdktools.OperatingSystem;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * JDK archive repository that is composed of multiple other repositories.
 * <p>
 *
 * Search behaviour is controlled by the configured {@link SearchType}.  It is possible when searching to return once some results have been found by a repository without
 * checking the others, or to always exhaustively search all repositories.
 */
public class CompositeJdkArchiveRepository implements JdkArchiveRepository<CompositeJdkArchiveRepository.WrappedJdkArtifact<?>>
{
    private final List<JdkArchiveRepository<?>> repositories;
    private final SearchType searchType;

    public CompositeJdkArchiveRepository(SearchType searchType, List<? extends JdkArchiveRepository<?>> repositories)
    {
        this.searchType = Objects.requireNonNull(searchType);
        this.repositories = List.copyOf(repositories);
    }

    @Override
    public Collection<? extends WrappedJdkArtifact<?>> search(JdkSearchRequest searchRequest)
    throws JdkRepositoryException
    {
        List<WrappedJdkArtifact<?>> allResults = new ArrayList<>();
        for (JdkArchiveRepository<?> repository : repositories)
        {
            Collection<? extends WrappedJdkArtifact<?>> curResults = searchRepository(searchRequest, repository);

            if (searchType == SearchType.FIRST_SUCCESS && !curResults.isEmpty())
                return curResults;
            else
                allResults.addAll(curResults);
        }

        return allResults;
    }

    private <A extends JdkArtifact> Collection<? extends WrappedJdkArtifact<A>> searchRepository(JdkSearchRequest searchRequest, JdkArchiveRepository<A> repository)
    throws JdkRepositoryException
    {
        Collection<? extends A> curResults = repository.search(searchRequest);

        return curResults.stream().map(r -> new WrappedJdkArtifact<>(repository, r)).collect(Collectors.toList());
    }

    @Override
    public JdkArchive resolveArchive(WrappedJdkArtifact<?> jdkArtifact)
    throws JdkRepositoryException
    {
        return resolveArchiveTypeSafe(jdkArtifact);
    }

    private <A extends JdkArtifact> JdkArchive resolveArchiveTypeSafe(WrappedJdkArtifact<A> wrappedJdkArtifact)
    throws JdkRepositoryException
    {
        return wrappedJdkArtifact.getSourceRepository().resolveArchive(wrappedJdkArtifact.getWrappedArtifact());
    }

    @Override
    public Collection<? extends JdkArchive> purgeCache(JdkPurgeCacheRequest jdkMatchSearchRequest) throws JdkRepositoryException
    {
        List<JdkArchive> archives = new ArrayList<>();
        for (JdkArchiveRepository<?> repository : repositories)
        {
            Collection<? extends JdkArchive> curPurgeResult = repository.purgeCache(jdkMatchSearchRequest);
            archives.addAll(curPurgeResult);
        }
        return archives;
    }

    /**
     * A JDK artifact that wraps another one from another repository, maintaining a reference to the original artifact and repository.
     *
     * @param <A> the underlying artifact type.
     */
    public static class WrappedJdkArtifact<A extends JdkArtifact> implements JdkArtifact
    {
        private final JdkArchiveRepository<A> sourceRepository;
        private final A artifact;

        protected WrappedJdkArtifact(JdkArchiveRepository<A> sourceRepository, A artifact)
        {
            this.sourceRepository = Objects.requireNonNull(sourceRepository);
            this.artifact = Objects.requireNonNull(artifact);
        }

        /**
         * @return the repository this search result was from.
         */
        public JdkArchiveRepository<A> getSourceRepository()
        {
            return sourceRepository;
        }

        /**
         * @return the wrapped search result.
         */
        public A getWrappedArtifact()
        {
            return artifact;
        }

        @Override
        public String getVendor()
        {
            return getWrappedArtifact().getVendor();
        }

        @Override
        public ArtifactVersion getVersion()
        {
            return getWrappedArtifact().getVersion();
        }

        @Override
        public Architecture getArchitecture()
        {
            return getWrappedArtifact().getArchitecture();
        }

        @Override
        public OperatingSystem getOperatingSystem()
        {
            return getWrappedArtifact().getOperatingSystem();
        }

        @Override
        public ArchiveType getArchiveType()
        {
            return getWrappedArtifact().getArchiveType();
        }

        @Override
        public ReleaseType getReleaseType()
        {
            return getWrappedArtifact().getReleaseType();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof WrappedJdkArtifact)) return false;
            WrappedJdkArtifact<?> that = (WrappedJdkArtifact<?>) o;
            return getSourceRepository().equals(that.getSourceRepository()) && getWrappedArtifact().equals(that.getWrappedArtifact());
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(getSourceRepository(), getWrappedArtifact());
        }

        @Override
        public String toString()
        {
            return new StringJoiner(", ", WrappedJdkArtifact.class.getSimpleName() + "[", "]")
                    .add("sourceRepository=" + sourceRepository)
                    .add("artifact=" + artifact)
                    .toString();
        }
    }

    /**
     * How searches are performed across multiple repositories.
     */
    public static enum SearchType
    {
        /**
         * Search all registered repositories and combine their results.
         */
        EXHAUSTIVE,

        /**
         * Search repositories until at least one result is found, then return those results without searching subsequent repositories.
         */
        FIRST_SUCCESS
    }
}
