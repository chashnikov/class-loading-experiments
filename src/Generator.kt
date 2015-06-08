/**
 * @author nik
 */
package generator

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.jar.JarOutputStream
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

fun main(args: Array<String>) {
    debug = true
    Generator(File("repo"), LayoutKind.MANY_JARS).generate()
}

private val classesInPackage = 10
private val packagesInPlugin = 10
private val packagesInPlatformModule = 10
private val subsystemsCount = 50
private val pluginsCount = 50
private var debug = false

enum class LayoutKind {
    SINGLE_JAR, MANY_JARS, DIRECTORIES
}

class Generator(val outRoot: File, val layout: LayoutKind) {
    fun generate() {
        if (debug) println("Clearing output directory")
        FileUtil.delete(outRoot)
        val outputPaths = HashMap<String, File>()
        val pluginDependencies = HashMap<String, List<String>>()
        val platformModules = ArrayList<String>()
        for (i in 1..subsystemsCount) {
            val name = "platform$i"
            generateModuleClasses(name, packagesInPlatformModule, emptyList(), outputPaths)
            platformModules.add(name)
        }
        val pluginModules = (1..pluginsCount).map {"plugin$it"}
        pluginModules.forEachIndexed { i, name ->
            val from = i % (subsystemsCount - 4)
            pluginDependencies[name] = platformModules.subList(from, from + 5)
            generateModuleClasses(name, packagesInPlugin, pluginDependencies[name], outputPaths)
        }

        val parentFile = outRoot.getAbsoluteFile().getParentFile()
        when (layout) {
            LayoutKind.SINGLE_JAR -> {
                saveModulesXml(outRoot, "platform", emptyList())
                packToJar("platform", platformModules)
                pluginModules.forEach {
                    saveModulesXml(outRoot, it, listOf("platform"))
                    packToJar(it, listOf(it))
                }
                saveModulesXml(parentFile, outRoot.getName(), (listOf("platform") + pluginModules).map { "$it/$it.jar" }, emptyList())
            }
            LayoutKind.MANY_JARS -> {
                platformModules.forEach {
                    saveModulesXml(outRoot, it, emptyList())
                    packToJar(it, listOf(it))
                }
                pluginModules.forEach {
                    saveModulesXml(outRoot, it, pluginDependencies[it])
                    packToJar(it, listOf(it))
                }
                saveModulesXml(parentFile, outRoot.getName(), (platformModules + pluginModules).map { "$it/$it.jar" }, emptyList())
            }
            else -> {
                platformModules.forEach {
                    saveModulesXml(outRoot, it, listOf("classes"), emptyList())
                }
                pluginModules.forEach {
                    saveModulesXml(outRoot, it, listOf("classes"), pluginDependencies[it])
                }
                saveModulesXml(parentFile, outRoot.getName(), (platformModules + pluginModules).map { "$it/classes" }, emptyList())
            }
        }
    }

    private fun packToJar(jarName: String, moduleNames: List<String>) {
        val jarFile = File(outRoot, "$jarName/$jarName.jar")
        FileUtil.createParentDirs(jarFile)
        val out = JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile)))
        val writtenPaths = HashSet<String>()
        moduleNames.forEach {
            val classesDir = File(outRoot, "$it/classes")
            ZipUtil.addDirToZipRecursively(out, jarFile, classesDir, "", null, writtenPaths)
            FileUtil.delete(classesDir)
        }
        out.close()
        if (jarName !in moduleNames) {
            moduleNames.forEach { File(outRoot, it).delete() }
        }
    }

    private fun generateModuleClasses(moduleName: String, packages: Int, depModules: List<String>, outputPaths: MutableMap<String, File>): File {
        val sourcesRoot = createTempDir("module-$moduleName-src", "")
        if (debug) println("Generating $moduleName...")
        for (p in 1..packages) {
            val packageName = "org.$moduleName.pack$p"
            generateClass(sourcesRoot, "$packageName.Base", methods(10))
            val impls = 1..classesInPackage-2
            for (i in impls) generateClass(sourcesRoot, "$packageName.Impl$i", methods(15), " extends Base")
            generateClass(sourcesRoot, "$packageName.Container", initMethod(impls.map { "Impl$it.init();" }))

        }
        generateClass(sourcesRoot, "org.$moduleName.Entry", initMethod(((1..packages).map {
            "org.$moduleName.pack$it.Container.init();"
        }
                + depModules.map {
            "org.$it.Entry.init();"
        })) + "\n{init();}")

        val compiler = ToolProvider.getSystemJavaCompiler()
        val toCompile = sourcesRoot.walkTopDown().toList().filter { it.isFile() }
        val diagnostic = DiagnosticCollector<JavaFileObject?>()
        val fileManager = compiler.getStandardFileManager(diagnostic, null, null)
        val javaFileObjects = fileManager.getJavaFileObjectsFromFiles(toCompile)

        val classesDir = File(outRoot, "$moduleName/classes")
        FileUtil.createDirectory(classesDir)
        val classpath = depModules.map { outputPaths[it]!!.getAbsolutePath() }.joinToString(File.pathSeparator)
        compiler.getTask(null, fileManager, diagnostic, listOf("-d", classesDir.getAbsolutePath(), "-classpath", classpath), null, javaFileObjects).call()
        if (diagnostic.getDiagnostics().isNotEmpty()) {
            for (d in diagnostic.getDiagnostics()) {
                println(d)
            }
            System.exit(1)
        }
        fileManager.close()
        FileUtil.delete(sourcesRoot)
        outputPaths[moduleName] = classesDir
        return classesDir
    }
}

fun generateClass(outRoot: File, name: String, body: String, extends: String = "") {
    val packageName = name.substring(0, name.lastIndexOf('.'))
    val className = name.substring(name.lastIndexOf('.') + 1)
    val file = File(outRoot, "${packageName.replace('.', '/')}/$className.java")
    file.getParentFile().mkdirs()
    file.writeText("""package $packageName;
public class $className $extends{
${body.split('\n').map { "    $it" }.joinToString("\n")}
}
""")
}

private fun initMethod(body: List<String>) = body.joinToString("\n", prefix = "public static void init(){\n", postfix = "}\n")

private fun methods(count: Int) = (1..count).map {"public void m$it(int p) {\n}"}.joinToString("\n") + "\npublic static void init(){}"

private fun saveModulesXml(repo: File, name: String, dependencies: List<String>) {
    saveModulesXml(repo, name, listOf("$name.jar"), dependencies)
}

private fun saveModulesXml(repo: File, name: String, roots: List<String>, dependencies: List<String>) {
    val moduleFile = File(repo, "$name/module.xml")
    FileUtil.createParentDirs(moduleFile)
    moduleFile.writeText("""
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.1" name="$name">
    <resources>
${roots.map {
"""              <resource-root path="$it"/>
"""
    }.joinToString("")
    }
    </resources>
    <dependencies>
${dependencies.map {
        """      <module name="$it"/>
"""}.joinToString("")
    }
    </dependencies>
</module>
""")
}