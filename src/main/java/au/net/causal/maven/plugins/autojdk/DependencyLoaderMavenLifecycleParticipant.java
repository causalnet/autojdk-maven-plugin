package au.net.causal.maven.plugins.autojdk;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.cli.internal.BootstrapCoreExtensionManager;
import org.apache.maven.cli.internal.extension.model.CoreExtension;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.extension.internal.CoreExtensionEntry;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This extension base class can be used to work around a Maven extension not having its dependencies on its classpath when loaded from lib/ext or
 * maven.ext.class.path, as opposed to loaded as a project extension or from .mvn/extensions.xml where extensions do get their dependencies included on their
 * classpath.  To use, instead of declaring your extension directly as a Plexus component, add a subclass of this class that references your
 * original extension class.  This class will, as part of the extension lifecycle, instantiate your extension with dependencies on its classpath as needed and then
 * delegate all its lifecycle methods.
 * This will only be done when needed - if the extension is being loaded from extensions.xml or from a project where dependencies are already present, it will
 * simply instantiate the extension as-is.
 * <p>
 *
 * Dependency injection into the target extension is not supported.
 */
public abstract class DependencyLoaderMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant
{
    private final ExtensionSpec extensionSpec;
    private final String extensionVersion;
    private final Class<? extends AbstractMavenLifecycleParticipant> extensionClass;

    @Requirement
    private PlexusContainer container;

    /**
     * Lazily created extension instance.
     */
    private AbstractMavenLifecycleParticipant extensionInstance;

    /**
     * Constructor.
     *
     * @param extensionSpec groupId/artifactId of your extension.
     * @param extensionClass the target extension class that should be instantiated.
     */
    protected DependencyLoaderMavenLifecycleParticipant(ExtensionSpec extensionSpec, Class<? extends AbstractMavenLifecycleParticipant> extensionClass)
    {
        this.extensionSpec = Objects.requireNonNull(extensionSpec);
        this.extensionClass = Objects.requireNonNull(extensionClass);
        this.extensionVersion = lookupExtensionVersion(extensionSpec, extensionClass);
    }

    /**
     * Looks up the version of an extension from Maven metadata on its classpath.
     *
     * @param extensionSpec extension groupId/artifactId.
     * @param extensionClass the extension class.
     *
     * @return the version of the extension that is on the classpath.
     *
     * @throws RuntimeException if an error occurs reading the version.
     */
    private String lookupExtensionVersion(ExtensionSpec extensionSpec, Class<? extends AbstractMavenLifecycleParticipant> extensionClass)
    {
        try
        {
            return PluginMetadataTools.lookupArtifactVersion(extensionSpec.getGroupId(), extensionSpec.getArtifactId(), extensionClass);
        }
        catch (PluginMetadataTools.ArtifactMetadataException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets an existing instance or creates a new one of the target extension class.
     *
     * @param session Maven session.
     *
     * @return an instance of the target extension class.
     *
     * @throws MavenExecutionException if an error occurs during instantiation.
     */
    private synchronized AbstractMavenLifecycleParticipant getExtensionInstance(MavenSession session)
    throws MavenExecutionException
    {
        //Lazily create the instance
        if (extensionInstance != null)
            return extensionInstance;

        BootstrapCoreExtensionManager bootstrapCoreExtensionManager;
        try
        {
            bootstrapCoreExtensionManager = container.lookup(BootstrapCoreExtensionManager.class);

            //If we get here, we are running from lib/ext which means we don't have any of our dependencies on the classpath
            //Need to instantiate the extension using the proper realm
            extensionInstance = createExtensionInstanceUsingNewClassloader(bootstrapCoreExtensionManager, session);
        }
        catch (ComponentLookupException e)
        {
            //Happens if we are not loading from lib/ext but from extensions.xml or from project extension
            //Good thing is we can just load normally from here
            extensionInstance = createExtensionInstanceUsingOurClassloader();
        }

        return extensionInstance;
    }

    private AbstractMavenLifecycleParticipant createExtensionInstanceUsingOurClassloader()
    throws MavenExecutionException
    {
        try
        {
            return extensionClass.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new MavenExecutionException("Error creating extension instance " + extensionClass.getName() + ": " + e, e);
        }
    }

    private AbstractMavenLifecycleParticipant createExtensionInstanceUsingNewClassloader(BootstrapCoreExtensionManager bootstrapCoreExtensionManager,
                                                                                         MavenSession session)
    throws MavenExecutionException
    {
        //Very important that if we get here we are running as ext using the core loader (plexus.core realm)
        //and we definitely don't have our own isolated loader
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Set<String> providedArtifacts;
        ClassRealm classRealm;
        if (loader instanceof ClassRealm)
        {
            CoreExtensionEntry coreEntry = CoreExtensionEntry.discoverFrom((ClassRealm) loader);
            providedArtifacts = coreEntry.getExportedArtifacts();
            classRealm = (ClassRealm)loader;
        }
        else //Can't look it up, do the best we can
        {
            providedArtifacts = Set.of();
            classRealm = null;
        }

        CoreExtension coreExtension = new CoreExtension();
        coreExtension.setGroupId(extensionSpec.getGroupId());
        coreExtension.setArtifactId(extensionSpec.getArtifactId());
        coreExtension.setVersion(extensionVersion);

        List<CoreExtension> coreExtensions = List.of(coreExtension);

        ClassRealm extensionRealm;
        try
        {
            List<CoreExtensionEntry> coreExtensionEntries = bootstrapCoreExtensionManager.loadCoreExtensions(session.getRequest(), providedArtifacts, coreExtensions);
            if (coreExtensionEntries.isEmpty())
                throw new MavenExecutionException("Unexpected empty core extension entries.", (Exception)null);

            extensionRealm = coreExtensionEntries.get(0).getClassRealm();

        }
        catch (DuplicateRealmException e)
        {
            //This happens when the extension might have been loaded both with lib/ext and with extensions.xml
            //Pretty are but it might happen
            //We know we are lib/ext, but there is another classrealm with the extension loaded already

            //If we don't have a realm loader ourselves, just bail out, nothing we can do
            if (classRealm == null)
                throw new MavenExecutionException("Error loading extension: " + e, e);

            //Otherwise can just lookup the already-existing realm ourselves
            extensionRealm = classRealm.getWorld().getClassRealm(e.getId());
        }
        catch (Exception e) //Alas loadCoreExtensions() throws generic Exception so we need to catch
        {
            throw new MavenExecutionException("Error loading extension: " + e, e);
        }

        //Reload the extension class under the new loader, which will give it all its dependencies
        try
        {
            Class<? extends AbstractMavenLifecycleParticipant> extensionClassReloaded =
                    Class.forName(extensionClass.getName(), true, extensionRealm).asSubclass(AbstractMavenLifecycleParticipant.class);

            return extensionClassReloaded.getConstructor().newInstance();
        }
        catch (ClassNotFoundException e)
        {
            //Would only happen if the class is not found in the extension classloader
            throw new MavenExecutionException("Could not find extension class in extension classloader: " + e, e);
        }
        catch (ReflectiveOperationException e)
        {
            throw new MavenExecutionException("Could not instantiate extension " + extensionClass + ": " + e, e);
        }
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException
    {
        AbstractMavenLifecycleParticipant extensionInstance = getExtensionInstance(session);

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(extensionInstance.getClass().getClassLoader());
            extensionInstance.afterProjectsRead(session);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException
    {
        AbstractMavenLifecycleParticipant extensionInstance = getExtensionInstance(session);

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(extensionInstance.getClass().getClassLoader());
            extensionInstance.afterSessionStart(session);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException
    {
        AbstractMavenLifecycleParticipant extensionInstance = getExtensionInstance(session);

        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(extensionInstance.getClass().getClassLoader());
            extensionInstance.afterSessionEnd(session);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
    }

    /**
     * Extension specifier by group ID and artifact ID.
     */
    protected static class ExtensionSpec
    {
        private final String groupId;
        private final String artifactId;

        public ExtensionSpec(String groupId, String artifactId)
        {
            this.groupId = Objects.requireNonNull(groupId);
            this.artifactId = Objects.requireNonNull(artifactId);
        }

        public String getGroupId()
        {
            return groupId;
        }

        public String getArtifactId()
        {
            return artifactId;
        }

        @Override
        public String toString()
        {
            return getGroupId() + ":" + getArtifactId();
        }
    }
}
