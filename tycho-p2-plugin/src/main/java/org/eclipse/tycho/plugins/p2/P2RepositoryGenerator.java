package org.eclipse.tycho.plugins.p2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.tycho.p2maven.tools.TychoFeaturesAndBundlesPublisherApplication;
import org.eclipse.tycho.packaging.RepositoryGenerator;

@Component(role = RepositoryGenerator.class, hint = "p2")
public class P2RepositoryGenerator implements RepositoryGenerator {

    @Requirement
    Logger logger;

    @Requirement
    IProvisioningAgent agent;

    @Override
    public File createRepository(List<MavenProject> projects, File destination)
            throws IOException, MojoExecutionException, MojoFailureException {
        agent.getService(Object.class);
        TychoFeaturesAndBundlesPublisherApplication application = new TychoFeaturesAndBundlesPublisherApplication() {
            @Override
            protected IPublisherAction[] createActions() {
                List<IPublisherAction> actions = new ArrayList<IPublisherAction>();
                for (MavenProject project : projects) {
                    File file = project.getArtifact().getFile();
                    actions.add(new BundlesAction(new File[] { file }));
                }
                return actions.toArray(IPublisherAction[]::new);
            }

            @Override
            protected void setupAgent() throws ProvisionException {
                this.agent = P2RepositoryGenerator.this.agent;
            }
        };
        File repository = new File(destination, "p2");
        repository.mkdirs();
        Builder<Object> arguments = Stream.builder();
        arguments.add("-artifactRepository");
        arguments.add(repository.toURI().toString());
        arguments.add("-metadataRepository");
        arguments.add(repository.toURI().toString());
        Object result;
        try {
            result = application.run(arguments.build().toArray(String[]::new));
        } catch (Exception e) {
            throw new MojoFailureException(e);
        }
        return null;
    }

}
