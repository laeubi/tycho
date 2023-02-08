package org.eclipse.tycho.adoptium;

import static java.net.HttpURLConnection.HTTP_OK;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Priority;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.toolchain.DefaultToolchainManager;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainFactory;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.apache.maven.toolchain.model.ToolchainModel;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component(role = ToolchainManager.class, hint = "adoptium")
@Priority(10)
public class AdoptiumToolChainProvider extends DefaultToolchainManager implements ToolchainManager {

	private static final String VERSION_REQUIREMENT = "version";
	private static final String OS_REQUIREMENT = "os";
	private static final String ARCH_REQUIREMENT = "arch";

	private static final String TYPE_JDK = "jdk";

	HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20))
			.build();

	@Requirement
	Logger logger;

	@Requirement
	ArchiverManager archiverManager;

	@Requirement(hint = "jdk")
	ToolchainFactory toolchainFactory;

	@Override
	public Toolchain getToolchainFromBuildContext(String type, MavenSession context) {
		return super.getToolchainFromBuildContext(type, context);
	}

	@Override
	public List<Toolchain> getToolchains(MavenSession session, String type, Map<String, String> requirements) {
		List<Toolchain> toolchains = super.getToolchains(session, type, requirements);
		if (toolchains.isEmpty() && TYPE_JDK.equals(type) && requirements != null
				&& requirements.containsKey(VERSION_REQUIREMENT) && requirements.containsKey(OS_REQUIREMENT)
				&& requirements.containsKey(ARCH_REQUIREMENT)) {
			try {
				String version = requirements.get(VERSION_REQUIREMENT);
				String os = requirements.get(OS_REQUIREMENT);
				String arch = requirements.get(ARCH_REQUIREMENT);
				if (version.startsWith("1.")) {
					version = version.substring(2);
				}
				logger.info("Requesting a JDK with version " + version + " that is not in standard toolchains...");
				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getLatestUrl(version, os, arch)))
						.timeout(Duration.ofMinutes(2)).header("Accept", "application/json").build();
				HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
				if (response.statusCode() == HTTP_OK) {
					JsonParser jsonParser = new JsonParser();
					JsonArray options = jsonParser.parse(response.body()).getAsJsonArray();
					for (JsonElement element : options) {
						JsonObject jdk = element.getAsJsonObject();
						JsonObject binary = jdk.getAsJsonObject("binary");
						String releaseName = jdk.getAsJsonPrimitive("release_name").getAsString();
						String releaseLink = jdk.getAsJsonPrimitive("release_link").getAsString();
						JsonObject jdkpackage = binary.getAsJsonObject("package");
						String checksum = jdkpackage.getAsJsonPrimitive("checksum").getAsString();
						String link = jdkpackage.getAsJsonPrimitive("link").getAsString();
						String filename = jdkpackage.getAsJsonPrimitive("name").getAsString();
						ArtifactRepository repository = session.getLocalRepository();
						String basedir = repository.getBasedir();
						DefaultArtifact artifact = new DefaultArtifact(jdk.getAsJsonPrimitive("vendor").getAsString(),
								"jdk",
								jdk.getAsJsonObject("version").getAsJsonPrimitive("openjdk_version").getAsString(),
								"runtime", "jdk", "", new DefaultArtifactHandler("bin"));
						String pathOf = repository.getLayout().pathOf(artifact);
						File destination = new File(new File(basedir, pathOf).getParentFile(), filename);
						File baseFolder = destination.getParentFile();
						if (!destination.exists()) {
							baseFolder.mkdirs();
							logger.info("Downloading " + link + " (" + checksum + ") to " + destination + "...");
							HttpResponse<Path> httpResponse = client.send(
									HttpRequest.newBuilder().uri(URI.create(link)).build(),
									BodyHandlers.ofFile(destination.toPath()));
							int statusCode = httpResponse.statusCode();
							if (statusCode != 200) {
								logger.error("Server returned: " + statusCode + "!");
								destination.delete();
								return toolchains;
							}
						}
						File jvmLocation = new File(baseFolder, FilenameUtils.getBaseName(destination.getName()));
						if (!jvmLocation.exists()) {
							jvmLocation.mkdirs();
							UnArchiver unArchiver = archiverManager.getUnArchiver(destination);
							unArchiver.setDestDirectory(jvmLocation);
							unArchiver.setSourceFile(destination);
							unArchiver.extract();
						}
						File javaHome = findJavaHome(jvmLocation);
						if (javaHome != null) {
							System.out.println("JavaHome is: " + javaHome);
							// return new JavaT
							ToolchainModel model = new ToolchainModel();
							model.setType("jdk");
							Xpp3Dom configuration = new Xpp3Dom("configuration");
							Xpp3Dom home = new Xpp3Dom("jdkHome");
							home.setValue(javaHome.getAbsolutePath());
							configuration.addChild(home);
							model.setConfiguration(configuration);
							Properties provides = new Properties();
							provides.setProperty("id", "JavaSE-" + requirements.get(VERSION_REQUIREMENT));
							provides.setProperty(VERSION_REQUIREMENT, requirements.get(VERSION_REQUIREMENT));
							model.setProvides(provides);
							ToolchainPrivate toolchain = toolchainFactory.createToolchain(model);
							return List.of(toolchain);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return toolchains;
	}

	private File findJavaHome(File loc) {
		if (new File(loc, "bin/java").isFile() || new File(loc, "bin/java.exe").isFile()) {
			return loc;
		}
		for (File child : loc.listFiles()) {
			if (child.isDirectory()) {
				File javaHome = findJavaHome(child);
				if (javaHome != null) {
					return javaHome;
				}
			}
		}
		return null;
	}

	private String getLatestUrl(String version, String os, String arch) {
		// TODO other adjustments needed?!?
		if ("x86_64".equals(arch)) {
			arch = "x64";
		}
		return String.format(
				"https://api.adoptium.net/v3/assets/latest/%s/hotspot?architecture=%s&image_type=jdk&os=%s&vendor=eclipse",
				version, arch, os);
	}

}
