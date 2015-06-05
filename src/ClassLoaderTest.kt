/**
 * @author nik
 */
package classLoaders

import com.intellij.util.lang.UrlClassLoader
import java.io.File
import java.net.URL
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

val standardPerPluginUrlClassLoader = PerPluginClassLoader(::createStandardUrlClassLoader, "standard")
val ourPerPluginUrlClassLoader = PerPluginClassLoader(::createOurUrlClassLoader, "our")
val standardCommonUrlClassLoader = CommonClassLoader(::createStandardUrlClassLoader, "standard")
val ourCommonUrlClassLoader = CommonClassLoader(::createOurUrlClassLoader, "our")
private val allKinds = listOf(standardPerPluginUrlClassLoader, ourPerPluginUrlClassLoader, standardCommonUrlClassLoader,
        ourCommonUrlClassLoader)

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
    val counters = listOf(
            sun.misc.PerfCounter.getFindClasses(),
            sun.misc.PerfCounter.getFindClassTime(),
            sun.misc.PerfCounter.getParentDelegationTime(),
            sun.misc.PerfCounter.getReadClassBytesTime()
    )
    val plugins = factory.createLoaders(repo)
    val duration = measureTimeMillis {
        for ((moduleName, loader) in plugins) {
            val aClass = Class.forName("org.$moduleName.Entry", true, loader)
            aClass.newInstance()
        }
    }
    println("$factory: ${plugins.size()} plugins loaded in ${duration}ms")
    counters.forEach {println(it)}
    counters.forEach { it.set(0) }
    println("==========")
}

fun main(args: Array<String>) {
    repeat(2) {
        allKinds.forEach {
            test(File("repo"), it)
        }
    }
}
/*
UrlClassLoader, separate jars, separate loaders:
sun.classloader.findClasses = 3929
sun.classloader.findClassTime = 829630482
sun.classloader.parentDelegationTime = 661464219
sun.urlClassLoader.readClassBytesTime = 15467969
50 plugins loaded in 1444ms

URLClassLoader, separate jars, separate loaders:
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 793675884
sun.classloader.parentDelegationTime = 661767856
sun.urlClassLoader.readClassBytesTime = 250237847
50 plugins loaded in 1491ms

UrlClassLoader, separate jars, single loader:
sun.classloader.findClasses = 3929
sun.classloader.findClassTime = 875458856
sun.classloader.parentDelegationTime = 591688374
sun.urlClassLoader.readClassBytesTime = 15442300
50 plugins loaded in 1417ms

URLClassLoader, separate jars, single loader
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 856273435
sun.classloader.parentDelegationTime = 568003825
sun.urlClassLoader.readClassBytesTime = 238703796
50 plugins loaded in 1442ms

UrlClassLoader, single jar:
sun.classloader.findClasses = 3922
sun.classloader.findClassTime = 837644812
sun.classloader.parentDelegationTime = 587112273
sun.urlClassLoader.readClassBytesTime = 15295192
50 plugins loaded in 1379ms

URLClassLoader, single jar
sun.classloader.findClasses = 3810
sun.classloader.findClassTime = 755237556
sun.classloader.parentDelegationTime = 581553481
sun.urlClassLoader.readClassBytesTime = 246043223
50 plugins loaded in 1362ms



*/

