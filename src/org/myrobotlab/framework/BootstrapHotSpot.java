package org.myrobotlab.framework;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.myrobotlab.service.interfaces.Bootstrap;

/**
 * @author GroG
 * 
 * 
 *         References :
 * 
 *         http://www.excelsior-usa.com/articles/java-to-exe.html
 * 
 *         possible small wrappers mac / linux / windows - http://mypomodoro
 *         .googlecode.com/svn-history/r89/trunk/src/main/java/org
 *         /mypomodoro/util/Restart.java
 * 
 *         http://java.dzone.com/articles/programmatically-restart-java
 *         http://stackoverflow
 *         .com/questions/3468987/executing-another-application-from-java
 * 
 * 
 *         TODO - ARMV 6 7 8 ??? -
 *         http://www.binarytides.com/linux-cpu-information/ - lscpu
 * 
 *         Architecture: armv7l Byte Order: Little Endian CPU(s): 4 On-line
 *         CPU(s) list: 0-3 Thread(s) per core: 1 Core(s) per socket: 1
 *         Socket(s): 4
 * 
 * 
 *         TODO - soft floating point vs hard floating point readelf -A
 *         /proc/self/exe | grep Tag_ABI_VFP_args soft = nothing hard =
 *         Tag_ABI_VFP_args: VFP registers
 * 
 *         PACKAGING jsmooth - windows only javafx - 1.76u - more dependencies ?
 *         http://stackoverflow.com/questions/1967549/java-packaging-tools-
 *         alternatives-for-jsmooth-launch4j-onejar
 * 
 * 
 */
public class BootstrapHotSpot implements Bootstrap {

	static int BUFFER_SIZE = 2048;
	// BAD BAD BUG LEAVING IT COMMENTED
	// THE DANGERS OF STATIC INITIALIZATION !!
	// static PreLogger log = PreLogger.getInstance();
	PreLogger log;

	// TODO classpath order - for quick bleeding edge updates?
	// rsync exploded classpath

	// TODO - check for Java 1.7 or >
	// TODO - addShutdownHook

	// TODO - check for network connectivity
	// TODO - addShutdownHook

	// TODO - proxy
	// -Dhttp.proxyHost=webproxy -Dhttp.proxyPort=80
	// -Dhttps.proxyHost=webproxy -Dhttps.proxyPort=80
	// -Dhttp.proxyUserName="myusername" -Dhttp.proxyPassword="mypassword"

	// TODO? how to get vm args
	// http://stackoverflow.com/questions/1490869/how-to-get-vm-arguments-from-inside-of-java-application
	// http://java.dzone.com/articles/programmatically-restart-java
	// http://stackoverflow.com/questions/9911686/getresource-some-jar-returns-null-although-some-jar-exists-in-geturls
	// RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
	// List<String> arguments = runtimeMxBean.getInputArguments();

	public BootstrapHotSpot() {
	}

	private class StreamGobbler extends Thread {
		InputStream is;
		String type;

		private StreamGobbler(InputStream is, String type) {
			this.is = is;
			this.type = type;
		}

		@Override
		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null) {
					// System.out.println(type + "> " + line);
					log.info(type + "> " + line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	public List<String> getJVMArgs() {
		RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
		return runtimeMxBean.getInputArguments();
	}

	public synchronized void spawn(String[] in) throws IOException, URISyntaxException, InterruptedException {
		log = PreLogger.getInstance();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
		log.info(String.format("\n\nBootstrap starting spawn %s", formatter.format(new Date())));

		log.info("============== args begin ==============");
		List<String> jvmArgs = getJVMArgs();

		log.info(String.format("jvmArgs %s", Arrays.toString(jvmArgs.toArray())));
		log.info(String.format("in %s", Arrays.toString(in)));
		log.info("============== args end ==============");

		// FIXME - details on space / %20 decoding in URI
		// http://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
		String protectedDomain = URLDecoder.decode(BootstrapHotSpot.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath(), "UTF-8");

		// this is null in jar :P
		// String location = File.class.getResource(".").getPath();

		log.info(String.format("protected domain %s", protectedDomain));
		// log.info(String.format("location %s", location));

		// platform id
		Platform platform = Platform.getLocalInstance();
		String platformId = platform.getPlatformId();
		log.info("platform " + platformId);
		String ps = File.pathSeparator;
		String fs = System.getProperty("file.separator");

		ArrayList<String> outArgs = new ArrayList<String>();

		// FIXME FIXME FIXME - it SHOULD NOT be variable path name !!!
		// java setup
		// String class path = System.getProperty("java.class.path"); // wtf -
		// do
		// we have to do this?
		String classpath = String.format("./%s./myrobotlab.jar%s./libraries/jar/*", ps, ps);
		// the java which is executing me will be the java executing runtime
		// java vs javaw ?

		String javaExe = platform.isWindows() ? "javaw" : "java";

		String javaPath = System.getProperty("java.home") + fs + "bin" + fs + javaExe;
		// JNI
		String jniLibraryPath = String.format("-Djava.library.path=libraries/native/%s", platformId);
		// FIXME - JNA path

		// String jvmMemory = "-Xmx2048m -Xms256m";
		Integer totalMemory = getTotalPhysicalMemory();
		if (totalMemory == null) {
			log.info("could not get total physical memory");
		} else {
			log.info("total physical memory returned is %d Mb", totalMemory);
		}

		/*
		 * mebbe windows does not like quotes anymore ? if
		 * (platform.isWindows()) { outArgs.add(String.format("\"%s\"",
		 * javaPath)); } else { outArgs.add(javaPath); }
		 */
		outArgs.add(javaPath);

		// transferring original jvm args
		/*
		 * DO NOT TRANSFER ALL DETAILS SHOULD BE DERIVED HERE - NOT PULLED FROM
		 * THE DEFINITION OF THIS CURRENTLY RUN JVM
		 */
		/*
		 * for (int i = 0; i < jvmArgs.size(); ++i) { String jvmArg =
		 * jvmArgs.get(i); if (!jvmArg.startsWith("-agentlib")) {
		 * outArgs.add(jvmArgs.get(i)); } }
		 */

		outArgs.add(jniLibraryPath);
		outArgs.add("-cp");
		outArgs.add(classpath);

		// outArgs.add(classpath);
		// outArgs.add("org.myrobotlab.service.Runtime"); DOUBLE ENTRY !

		boolean hasService = false;
		for (int i = 0; i < in.length; ++i) {
			String arg = in[i];
			if (arg.startsWith("org.myrobotlab.service")) {
				hasService = true;
			}
		}

		if (!hasService) {
			outArgs.add("org.myrobotlab.service.Runtime");
		}

		// TODO preserve/serialize command line parameters
		if (in.length > 0) {
			for (int i = 0; i < in.length; ++i) {
				outArgs.add(in[i]);
			}
		} else {
			// (default) - no parameters supplied

			outArgs.add("-service");
			outArgs.add("gui");
			outArgs.add("GUIService");
			outArgs.add("python");
			outArgs.add("Python");
		}

		// ProcessBuilder builder = new ProcessBuilder(path, "-Xmx1024m", "-cp",
		// classpath, ReSpawner.class.getName());

		if (Files.exists(Paths.get("./update/myrobotlab.jar"))) {
			// attempt to process the update
			log.info("update exists archiving current");

			try {
				// if thrown - "file locked" then createBootstrapJar
				// IF THAT THROWS - GIVE UP !!!

				// update available - archive old file
				Path source = Paths.get("./myrobotlab.jar");
				File archiveDir = new File("./archive");
				archiveDir.mkdirs();
				Path target = Paths.get(String.format("./archive/myrobotlab.%s.jar", getVersion()));
				Files.move(source, target, REPLACE_EXISTING);

				// copy update
				log.info("moving update");
				source = Paths.get("./update/myrobotlab.jar");
				target = Paths.get("./myrobotlab.jar");
				Files.move(source, target, REPLACE_EXISTING);
				log.info("moved update !");
			} catch (FileSystemException e) {
				try {
					// FIXME FIXME - normalize the start !!!!
					log.info(String.format("file myrobotlab.jar is locked - ejecting bootstrap.jar - %s", e.getMessage()));
					createBootstrapJar();

					ArrayList<String> bootArgs = new ArrayList<String>();

					bootArgs.add(javaPath);
					bootArgs.add("-jar");
					bootArgs.add("./bootstrap.jar"); // -jar uses manifest
					// bootArgs.add("org.myrobotlab.framework.Bootstrap");
					for (int i = 0; i < in.length; ++i) {
						bootArgs.add(in[i]);
					}
					String cmd = formatList(bootArgs);
					log.info(String.format("bootstrap.jar spawning -> [%s]", cmd));
					ProcessBuilder builder = new ProcessBuilder(bootArgs);
					Process process = builder.start();

					/*
					 * FIXME - THIS WILL BLOCK AND HOLD THE PROCESS OPEN !!!
					 * StreamGobbler errorGobbler = new
					 * StreamGobbler(process.getErrorStream(), "ERROR");
					 * 
					 * // any output? // StreamGobbler outputGobbler = new //
					 * StreamGobbler(process.getInputStream(), "OUTPUT");
					 * StreamGobbler outputGobbler = new
					 * StreamGobbler(process.getInputStream(), "OUTPUT");
					 * 
					 * // start gobblers outputGobbler.start();
					 * errorGobbler.start();
					 */

					// FIXME process.exitValue() will throw if process has not
					// exited
					// log.info(String.format("done - good luck new bootstrap - exitValue %d",
					// process.exitValue()));

					log.info(String.format("terminating - good luck new bootstrap :)"));
					PreLogger.close();
					System.exit(0);
					return;
				} catch (Exception ex) {
					log.error("PANIC - failed to create bootstrap - terminating - bye :(");
					PreLogger.close();
					log.error(ex);
					return;
				}
			}
		}

		String cmd = formatList(outArgs);
		log.info(String.format("spawning -> [%s]", cmd));
		/*
		 * ArrayList<String> test = new ArrayList<String>();
		 * test.add(String.format("%s", javaPath)); test.add("-cp");
		 * test.add("./myrobotlab.jar:./libraries/jar/*:./");
		 * test.add("org.myrobotlab.service.Runtime");
		 */
		ProcessBuilder builder = new ProcessBuilder(outArgs);// .inheritIO();

		// environment variables setup
		Map<String, String> env = builder.environment();
		if (platform.isLinux()) {
			String ldPath = String.format("'pwd'/libraries/native:'pwd'/libraries/native/%s:${LD_LIBRARY_PATH}", platformId);
			env.put("LD_LIBRARY_PATH", ldPath);
		} else if (platform.isMac()) {
			String dyPath = String.format("'pwd'/libraries/native:'pwd'/libraries/native/%s:${DYLD_LIBRARY_PATH}", platformId);
			env.put("DYLD_LIBRARY_PATH", dyPath);
		} else if (platform.isWindows()) {
			String path = String.format("PATH=%%CD%%\\libraries\\native;PATH=%%CD%%\\libraries\\native\\%s;%%PATH%%", platformId);
			env.put("PATH", path);
		} else {
			log.error("unkown operating system");
		}

		// ========== extract if necessary begin ==========
		// check to see if jar directory exists
		File jar = new File("./libraries/jar");
		if (!jar.exists()) {
			jar.mkdirs();
			// TODO - write to preLog.txt and pre-pend/append that to
			// myrobotlab.log !
			// stat all files if !stat extract
			// FIXME - list contents of zip directory

			// extract absolutely required dependencies
			log.info("extracting ./libraries/jar");
			extract("./myrobotlab.jar", "./", "resource/framework/root/", true);
		} else {
			log.info("./libraries/jar already exists, skipping extraction");
		}

		if (!jar.exists() || !jar.isDirectory()) {
			log.error("ERROR libraries/jar does not exist or is not a directory");
		}
		// ========== extract if necessary end ==========

		// you could start more than one process

		// ProcessBuilder.inheritIO() is a little pointless since the Bootstrap
		// is a sabot which
		// will be discarded and terminate - hence no std/io
		// Process process = builder.start();
		// TODO hold on to process - check for zombies - agent control
		// http://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id
		Process process = builder.start();

		/*
		 * FIXME ??? MAYBE THIS IS OK - BUT ON THE BOOTLOADER IT HANGS
		 * StreamGobbler errorGobbler = new
		 * StreamGobbler(process.getErrorStream(), "ERROR");
		 * 
		 * // any output? // StreamGobbler outputGobbler = new //
		 * StreamGobbler(process.getInputStream(), "OUTPUT"); StreamGobbler
		 * outputGobbler = new StreamGobbler(process.getInputStream(),
		 * "OUTPUT");
		 * 
		 * // start gobblers outputGobbler.start(); errorGobbler.start();
		 */

		// int ret = process.waitFor();
		// log.info(String.format("process returned %d", ret));

		// Runtime.getRuntime().exec(outArgs.toArray(new
		// String[outArgs.size()]));

		// TODO make new filew or pre-pend to myrobotlab.log
		// THESE WILL HANG THE PROCESS !!!!!
		// inheritIO(process.getInputStream(), System.out);
		// inheritIO(process.getErrorStream(), System.err);
		// process.waitFor();

		log.info(String.format("Bootstrap finished spawn %s", formatter.format(new Date())));
		PreLogger.close();

	}

	public String formatList(ArrayList<String> args) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.size(); ++i) {
			sb.append(String.format("%s ", args.get(i)));
		}

		return sb.toString();
	}

	public String getVersion() {
		log = PreLogger.getInstance();
		InputStream isr = BootstrapHotSpot.class.getResourceAsStream("/resource/version.txt");
		if (isr == null) {
			log.error("can not find resource [/resource/version.txt]");
			return null;
		}
		return new String(toByteArray(isr));
	}

	public byte[] toByteArray(InputStream is) {
		log = PreLogger.getInstance();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {

			int nRead;
			byte[] data = new byte[16384];

			while ((nRead = is.read(data, 0, data.length)) != -1) {
				baos.write(data, 0, nRead);
			}

			baos.flush();
			baos.close();
			return baos.toByteArray();
		} catch (Exception e) {
			log.error(e);
		}

		return null;
	}

	static public void extract(String resourcePath, String targetDirectory, String filter, boolean overwrite) throws IOException {
		// log.debug(String.format("extractFromResource (%s,%s)", resourcePath,
		// targetDirectory));
		InputStream source = new FileInputStream(resourcePath);

		File target = new File(targetDirectory);

		if (!target.exists() && !target.mkdirs()) {
			source.close();
			throw new IOException("Unable to create directory " + target.getAbsolutePath());
		}

		ZipInputStream in = new ZipInputStream(source);
		try {

			byte[] buffer = new byte[BUFFER_SIZE];

			for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
				// log.info(entry.getName());

				if (filter == null || entry.getName().startsWith(filter)) {

					String filename = entry.getName().substring(filter.length());
					// File file = new File(target, entry.getName());
					File file = new File(target, filename);

					// log.debug("Extracted Resource = " + entry.getName());
					if (entry.isDirectory()) {
						file.mkdirs();
					} else {
						file.getParentFile().mkdirs();
						if (!file.exists() || overwrite) {
							OutputStream out = new FileOutputStream(file);
							try {
								int count;
								while ((count = in.read(buffer)) > 0) {
									out.write(buffer, 0, count);
								}
							} finally {
								out.close();
							}
						}
						in.closeEntry();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			in.close();
		}
	}

	public void createBootstrapJar() throws IOException {
		ArrayList<String> resources = new ArrayList<String>();
		resources.add("/org/myrobotlab/framework/Bootstrap.class");
		resources.add("/org/myrobotlab/framework/Platform.class");
		resources.add("/org/myrobotlab/framework/PreLogger.class");
		resources.add("/resource/version.txt");
		createJarArchive("bootstrap.jar", resources);
	}

	/**
	 * takes an array list of filenames in current jar (TODO - make debuggable
	 * bin)
	 * 
	 * @param archiveFileName
	 * @param tobeJared
	 * @throws IOException
	 */
	public void createJarArchive(String archiveFileName, ArrayList<String> tobeJared) throws IOException {

		File archiveFile = new File(archiveFileName);
		byte buffer[] = new byte[BUFFER_SIZE];
		// Open archive file
		FileOutputStream stream = new FileOutputStream(archiveFile);
		Manifest manifest = new Manifest();

		// http://www.concretepage.com/java/add-manifest-into-jar-file-using-java
		Attributes global = manifest.getMainAttributes();
		global.put(Attributes.Name.MANIFEST_VERSION, "1.0.0");
		global.put(new Attributes.Name("Built-By"), String.format("Bootstrap %s", getVersion()));
		// global.put(new Attributes.Name("CLASS_PATH"), "dummy classpath");
		// global.put(new Attributes.Name("CONTENT_TYPE"), "txt/plain");
		// global.put(new Attributes.Name("SIGNATURE_VERSION"), "2.0");
		// global.put(new Attributes.Name("SPECIFICATION_TITLE"),
		// "dummy title");
		// global.put(new Attributes.Name("CLASS_PATH"), "dummy classpath");
		global.put(Attributes.Name.MAIN_CLASS, BootstrapHotSpot.class.getCanonicalName());

		JarOutputStream out = new JarOutputStream(stream, manifest);

		for (int i = 0; i < tobeJared.size(); i++) {

			String filename = tobeJared.get(i);
			System.out.println("Adding " + filename);

			// Add archive entry
			// TODO - must handle pathing
			String jarPath;
			if (filename.startsWith("/")) {
				jarPath = filename.substring(1);
			} else {
				jarPath = filename;
			}
			JarEntry jarAdd = new JarEntry(jarPath);
			// jarAdd.setTime(tobeJared[i].lastModified());
			out.putNextEntry(jarAdd);

			// Write file to archive
			// TODO must handle asResource

			// FileInputStream in = new FileInputStream(tobeJared[i]);
			InputStream in = File.class.getResourceAsStream(filename);
			if (in != null) {
				while (true) {
					int nRead = in.read(buffer, 0, buffer.length);
					if (nRead <= 0)
						break;
					out.write(buffer, 0, nRead);
				}
				in.close();
			} else {
				log.error(String.format("cant get %s as resouce stream", filename));
			}
		}

		out.close();
		stream.close();

		System.out.println("Adding completed OK");

	}

	public void spawn(List<String> args) throws IOException, URISyntaxException, InterruptedException {
		spawn(args.toArray(new String[args.size()]));
	}

	public Integer getTotalPhysicalMemory() {
		try {
			com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
			Integer physicalMemorySize = (int) (os.getTotalPhysicalMemorySize() / 1048576);

			return physicalMemorySize;
		} catch (Exception e) {
			log.error("getTotalPhysicalMemory - threw");
		}
		return null;
	}

	public static void main(String[] args) {
		try {
			System.out.println("starting bootstrap");
			BootstrapHotSpot bootstrap = new BootstrapHotSpot();
			bootstrap.spawn(args);
			System.out.println("leaving bootstrap");
		} catch (Exception e) {
			e.printStackTrace(System.out);
		} finally {
			System.exit(0);
		}
	}

}