package net.epicorp;

import java.io.*;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class PatchMC {
	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("Patch generator, a utility so you can easily see what was changed in each version of minecraft!");

		System.out.println("Credits:");
		System.out.println("Author: HalfOf2 (HalfOf2#0086)");
		System.out.println("Decompiler: https://github.com/hube12/DecompilerMC (Neil#4879)");
		System.out.println("Patch generator: http://gnuwin32.sourceforge.net/packages/diffutils.htm");

		if (args.length < 4) {
			args = new String[4];
			Scanner scanner = new Scanner(System.in);
			System.out.print("Enter the initial version: ");
			args[0] = scanner.nextLine();

			System.out.print("Enter the target version: ");
			args[1] = scanner.nextLine();

			System.out.print("Enter the output patch file: ");
			args[2] = scanner.nextLine();

			System.out.println("Client or Server: ");
			args[3] = scanner.nextLine();
		}

		boolean client = false;

		if (args[3].equalsIgnoreCase("y")) {
			client = true;
		}

		System.out.printf("Generating a %s from version %s to %s...\n", args[2], args[0], args[1]);

		System.out.println("Unpacking decompiler...");
		unloadResource("decomp1", "decompile/MCDecompiler.exe", "decompile/lib/cfr-0.146.jar", "decompile/lib/fernflower.jar", "decompile/lib/SpecialSource-1.8.6.jar");
		unloadResource("decomp2", "decompile/MCDecompiler.exe", "decompile/lib/cfr-0.146.jar", "decompile/lib/fernflower.jar", "decompile/lib/SpecialSource-1.8.6.jar");


		System.out.println("Decompiling...");
		Runtime runtime = Runtime.getRuntime();
		Process init = decompile(runtime, args[0], new File("decomp1/decompile/MCDecompiler.exe"), client);
		Process targ = decompile(runtime, args[1], new File("decomp2/decompile/MCDecompiler.exe"), client);

		System.out.println("This may take several minutes...");
		long start = System.currentTimeMillis();
		init.waitFor();
		write(init, "INITIAL_DECOMPILE");
		targ.waitFor();
		write(targ, "TARGET_DECOMPILE");

		System.out.printf("Decompiled server jars in %dms!\n", System.currentTimeMillis() - start);

		System.out.println("Unloading diff...");
		unloadResource("temp", "diff/diff.exe", "diff/libiconv2.dll", "diff/libintl3.dll");

		System.out.println("Generating patches...");
		ProcessBuilder process = genPatches(new File(args[2]), args[0], args[1]);
		process.start();

		System.out.println("Cleaning up...");
		fileList
			.stream()
				.map(File::toPath)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.filter(f -> !f.delete())
				.forEach(f -> System.out.printf("Error deleting %s!\n", f));

		System.out.println("Done.");
	}

	public static void write(Process process, String name) {
		System.out.printf("====== %s STD_OUT ======\n", name);
		StringBuilder stdout = new StringBuilder();
		new BufferedReader(new InputStreamReader(process.getInputStream())).lines().peek(l -> stdout.ensureCapacity(stdout.length() + l.length() + 2)).peek(l -> stdout.append('\t')).peek(stdout::append).peek(l -> stdout.append('\n'));
		System.out.println(stdout);
		System.out.printf("====== %s STD_OUT ======\n", name);

		System.out.printf("====== %s ERR_OUT ======\n", name);
		StringBuilder errout = new StringBuilder();
		new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().peek(l -> errout.ensureCapacity(errout.length() + l.length() + 2)).peek(l -> errout.append('\t')).peek(errout::append).peek(l -> errout.append('\n'));
		System.out.println(errout);
		System.out.printf("====== %s ERR_OUT ======\n", name);
	}

	public static ProcessBuilder genPatches(File output, String ver1, String ver2) {
		return new ProcessBuilder().command(new File("temp/diff/diff.exe").getAbsolutePath(), "-Nur", "\"" + new File("decomp1/decompile/src/" + ver1).getAbsolutePath() + "\"", "\"" + new File("decomp2/decompile/src/" + ver2).getAbsolutePath() + "\"").redirectOutput(output);
	}

	public static Process decompile(Runtime runtime, String version, File decompiler, boolean client) throws IOException {
		Process process = runtime.exec(decompiler + "", null, decompiler.getParentFile());
		PrintWriter stream = new PrintWriter(process.getOutputStream(), true);
		stream.println();
		stream.println();
		stream.println(version);
		if (client) {
			stream.println("c");
		} else {
			stream.println("s");
		}
		stream.println("y");
		stream.println();
		return process;
	}

	private static final byte[] BUFFER = new byte[1024];
	private static List<File> fileList = new LinkedList<>();

	public static void unloadResource(String dir, String... resources) throws IOException {
		File file = new File(dir);
		if (!file.mkdirs()) throw new RuntimeException(new FileSystemException("Some shit went down with " + dir));

		for (String resource : resources) {
			int read;
			File unload = new File(dir, resource);
			File parent = unload.getParentFile();
			if (!parent.exists()) parent.mkdirs();

			BufferedInputStream input = new BufferedInputStream(ClassLoader.getSystemResourceAsStream(resource));
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(unload));
			while ((read = input.read(BUFFER)) != -1) bos.write(BUFFER, 0, read);
			bos.flush(); // flush remaining data

			bos.close(); // close the file
			input.close();
		}

		fileList.add(file);
	}
}
