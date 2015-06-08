/**
 * @author nik
 */
package classLoaders

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.lang.UrlClassLoader
import generator.Generator
import generator.LayoutKind
import org.jboss.modules.ModuleIdentifier
import org.jboss.modules.ModuleLoader
import org.jboss.modules.MyLocalModuleFinder
import java.io.File
import java.net.URLClassLoader
import kotlin.util.measureTimeMillis

interface ClassLoaderFactory {
    fun createLoaders(repo: File): List<Pair<String, ClassLoader>>
}

class PerPluginClassLoader(val loaderFactory: (Array<File>,ClassLoader)->ClassLoader, val name: String): ClassLoaderFactory {
    override fun createLoaders(repo: File): List<Pair<String, ClassLoader>> {
        val platformLoader = loaderFactory(repo.listFiles {it.name.startsWith("platform")}!!, ClassLoader.getSystemClassLoader())
        return repo.listFiles {it.name.startsWith("plugin")}!!.map {Pair(it.name, loaderFactory(arrayOf(it), platformLoader))}
    }

    override fun toString() = "$name per-plugin class loader"
}

class CommonClassLoader(val loaderFactory: (Array<File>, ClassLoader) -> ClassLoader, val name: String): ClassLoaderFactory {
    override fun createLoaders(repo: File): List<Pair<String, ClassLoader>> {
        val loader = loaderFactory(repo.listFiles()!!, ClassLoader.getSystemClassLoader())
        return repo.listFiles {it.name.startsWith("plugin")}!!.map {Pair(it.name, loader)}
    }

    override fun toString() = "$name common class loader"
}

class JBossPerModuleClassLoader : ClassLoaderFactory {
    override fun createLoaders(repo: File): List<Pair<String, ClassLoader>> {
        val loader = ModuleLoader(arrayOf(MyLocalModuleFinder(arrayOf(repo))))
        return repo.listFiles { it.name.startsWith("plugin") }!!.map { Pair(it.name, loader.loadModule(ModuleIdentifier.create(it.name)).getClassLoader()) }
    }
}
class JBossCommonClassLoader: ClassLoaderFactory {
    override fun createLoaders(repo: File): List<Pair<String, ClassLoader>> {
        val loader = ModuleLoader(arrayOf(MyLocalModuleFinder(arrayOf(repo.getParentFile()))))
        val classLoader = loader.loadModule(ModuleIdentifier.create(repo.name)).getClassLoader()
        return repo.listFiles { it.name.startsWith("plugin") }!!.map { Pair(it.name, classLoader) }
    }
}

val jbossPerModuleClassLoader = JBossPerModuleClassLoader()
val jbossCommonClassLoader = JBossCommonClassLoader()
val standardPerPluginUrlClassLoader = PerPluginClassLoader(::createStandardUrlClassLoader, "standard")
val ourPerPluginUrlClassLoader = PerPluginClassLoader(::createOurUrlClassLoader, "our")
val standardCommonUrlClassLoader = CommonClassLoader(::createStandardUrlClassLoader, "standard")
val ourCommonUrlClassLoader = CommonClassLoader(::createOurUrlClassLoader, "our")
private val allKinds = listOf(standardPerPluginUrlClassLoader, ourPerPluginUrlClassLoader, standardCommonUrlClassLoader,
        ourCommonUrlClassLoader, jbossPerModuleClassLoader, jbossCommonClassLoader)

fun Array<File>.toJarRoots(): List<File> = map(::moduleRootToJarRoot)

fun moduleRootToJarRoot(root: File) =
    if (File(root, "classes").isDirectory()) {
        File(root, "classes")
    }
    else {
        File(root, "${root.name}.jar")
    }

private fun createStandardUrlClassLoader(moduleRoots: Array<File>, parent: ClassLoader) =
        URLClassLoader(moduleRoots.toJarRoots().map { it.toURI().toURL() }.toTypedArray(), parent)

private fun createOurUrlClassLoader(moduleRoots: Array<File>, parent: ClassLoader): UrlClassLoader {
    val urls = moduleRoots.toJarRoots().map { it.toURI().toURL() }.toTypedArray()
    return UrlClassLoader.build().parent(parent).urls(listOf(*urls)).allowLock().useCache().get()
}

fun test(repo: File, factory: ClassLoaderFactory) {
    val findClasses = sun.misc.PerfCounter.getFindClasses()
    val counters = listOf(
            findClasses,
            sun.misc.PerfCounter.getFindClassTime(),
            sun.misc.PerfCounter.getParentDelegationTime(),
            sun.misc.PerfCounter.getReadClassBytesTime()
    )
    var plugins = factory.createLoaders(repo)
    counters.forEach { it.set(0) }
    val duration = measureTimeMillis {
        for ((moduleName, loader) in plugins) {
            val aClass = Class.forName("org.$moduleName.Entry", true, loader)
            aClass.newInstance()
        }
    }
    println("$factory: ${plugins.size()} plugins loaded in ${duration}ms")
//    counters.filter{it!=findClasses}.forEach {println(it)}
//    println("==========")
}

fun main(args: Array<String>) {
//    test(File("repo"), jbossCommonClassLoader)
    testAll()
}

private fun testAll() {
    var i = 0
    FileUtil.delete(File("repo"))
    LayoutKind.values().forEach { layout ->
        println("Layout: $layout")
        allKinds.forEach {
            i++
            val repo = File("repo/repo$i")
            Generator(repo, layout).generate()
            test(repo, it)
        }
        println("============")
    }
}

