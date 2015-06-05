/**
 * @author nik
 */
package generator

import java.io.File
import javax.tools.ToolProvider
import java.util.ArrayList
import javax.tools.JavaFileObject
import javax.tools.DiagnosticCollector
import com.intellij.util.io.ZipUtil
import java.util.jar.JarOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.HashSet

val outRoot = File("gen")
val jarsDir = File("jars")
val singleJar = true
val classesInPackage = 5000

fun generateClass(outRoot: File, name: String, body: String, extends: String = "") {
    val packageName = name.substring(0, name.lastIndexOf('.'))
    val className = name.substring(name.lastIndexOf('.')+1)
    val file = File(outRoot, "${packageName.replace('.', '/')}/$className.java")
    file.getParentFile()!!.mkdirs()
    file.writeText("""package $packageName;
public class $className $extends{
${body.split('\n').map { "    $it" }.makeString("\n")}
}
""")
}

fun methods(count: Int) = (1..count).map {"public void m$it(int p) {\n}"}.makeString("\n")

fun generateModule(moduleName: String, packages: Int, depModules: List<String>) {
    println("Generating $moduleName...")
    val moduleOut = File(outRoot, moduleName)
    for (p in 1..packages) {
        val packageName = "org.$moduleName.pack$p"
        generateClass(moduleOut, "$packageName.Base", methods(10))
        val impls = 1..classesInPackage
        for (i in impls) generateClass(moduleOut, "$packageName.Impl$i", methods(15), " extends Base")
        generateClass(moduleOut, "$packageName.Container", impls.map { "public Impl$it impl$it = new Impl$it();"  }.makeString("\n"))
    }
    generateClass(moduleOut, "org.$moduleName.Entry", ((1..packages).map {
        "public org.$moduleName.pack$it.Container container$it = new org.$moduleName.pack$it.Container();"
    }
    + depModules.map {
        "public org.$it.Entry entry$it = new org.$it.Entry();"
    }).makeString("\n"))

    val compiler = ToolProvider.getSystemJavaCompiler()!!
    val toCompile = ArrayList<File>()
    moduleOut.recurse { if (it.isFile()) toCompile.add(it) }
    val diagnostic = DiagnosticCollector<JavaFileObject?>()
    val fileManager = compiler.getStandardFileManager(diagnostic, null, null)!!
//    val javaFileObjects = fileManager.getJavaFileObjects(toCompile.copyToArray())//todo[nik] report Kotlin bug
    val javaFileObjects = fileManager.getJavaFileObjectsFromFiles(toCompile)
    val classesDir = File(outRoot, "$moduleName-classes")
    classesDir.mkdirs()
    val classpath = depModules.map { File(outRoot, "$it-classes").getAbsolutePath() }.makeString(File.pathSeparator)
    compiler.getTask(null, fileManager, diagnostic, listOf("-d", classesDir.getAbsolutePath(), "-classpath", classpath), null, javaFileObjects)!!.call()
    if (diagnostic.getDiagnostics().notEmpty) {
        for (d in diagnostic.getDiagnostics()) {
            println(d)
        }
        System.exit(1)
    }
    fileManager.close()

    if (!singleJar) {
        File(classesDir, "deps.txt").writeText(depModules.makeString("\n"))
        File(classesDir, "entries.txt").writeText("org.$moduleName.Entry")

        jarsDir.mkdirs()
        val jarFile = File(jarsDir, "$moduleName.jar")
        val output = JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile)))
        ZipUtil.addDirToZipRecursively(output, jarFile, classesDir, "", null, null)
        output.close()
    }
}


fun main(args: Array<String>) {
    val toDelete = ArrayList<File>()
    outRoot.recurse { toDelete.add(it) }
    jarsDir.recurse { toDelete.add(it) }
    println("Clearing ${toDelete.size} files")
    toDelete.reverse().forEach { it.delete() }
    generateModule("util", 1, listOf())
    return
    val subsystems = (1..50).map { "platform$it" }
    for (s in subsystems) generateModule(s, 5, listOf("util"))
    for (p in 1..50) generateModule("plugin$p", 5, subsystems + listOf("util"))

    if (singleJar) {
        jarsDir.mkdirs()
        val jarFile = File(jarsDir, "all.jar")
        val output = JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile)))
        val written = HashSet<String>()
        outRoot.listFiles { it.name.endsWith("-classes") }!!.forEach {
            ZipUtil.addDirToZipRecursively(output, jarFile, it, "", null, written)
        }
        output.close()
    }
}