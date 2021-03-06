/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.internal.plugin

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.data.PluginDataStorage
import net.mamoe.mirai.console.internal.MiraiConsoleImplementationBridge
import net.mamoe.mirai.console.plugin.AbstractFilePluginLoader
import net.mamoe.mirai.console.plugin.PluginLoadException
import net.mamoe.mirai.console.plugin.jvm.JarPluginLoader
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.util.ConsoleExperimentalAPI
import net.mamoe.mirai.console.util.childScopeContext
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.net.URLClassLoader
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.streams.asSequence

internal object JarPluginLoaderImpl :
    AbstractFilePluginLoader<JvmPlugin, JvmPluginDescription>(".jar"),
    CoroutineScope,
    JarPluginLoader {

    override val configStorage: PluginDataStorage
        get() = MiraiConsoleImplementationBridge.dataStorageForJarPluginLoader

    private val logger: MiraiLogger = MiraiConsole.newLogger(JarPluginLoader::class.simpleName!!)

    @ConsoleExperimentalAPI
    override val dataStorage: PluginDataStorage
        get() = MiraiConsoleImplementationBridge.dataStorageForJarPluginLoader

    override val coroutineContext: CoroutineContext =
        MiraiConsole.childScopeContext("JarPluginLoader", CoroutineExceptionHandler { _, throwable ->
            logger.error("Unhandled Jar plugin exception: ${throwable.message}", throwable)
        })

    internal val classLoaders: MutableList<ClassLoader> = mutableListOf()

    @Suppress("EXTENSION_SHADOWED_BY_MEMBER") // doesn't matter
    override val JvmPlugin.description: JvmPluginDescription
        get() = this.description

    override fun Sequence<File>.extractPlugins(): List<JvmPlugin> {
        ensureActive()

        fun <T> ServiceLoader<T>.loadAll(file: File?): Sequence<T> {
            return stream().asSequence().mapNotNull {
                kotlin.runCatching {
                    it.type().kotlin.objectInstance ?: it.get()
                }.onFailure {
                    logger.error("Cannot load plugin ${file ?: "<no-file>"}", it)
                }.getOrNull()
            }
        }

        val inMemoryPlugins =
            ServiceLoader.load(
                JvmPlugin::class.java,
                generateSequence(MiraiConsole::class.java.classLoader) { it.parent }.last()
            ).loadAll(null)

        val filePlugins = this.associateWith {
            URLClassLoader(arrayOf(it.toURI().toURL()), MiraiConsole::class.java.classLoader)
        }.onEach { (_, classLoader) ->
            classLoaders.add(classLoader)
        }.mapValues {
            ServiceLoader.load(JvmPlugin::class.java, it.value)
        }.flatMap { (file, loader) ->
            loader.loadAll(file)
        }

        return (inMemoryPlugins + filePlugins).toSet().toList()
    }

    @Throws(PluginLoadException::class)
    override fun load(plugin: JvmPlugin) {
        ensureActive()
        runCatching {
            if (plugin is JvmPluginInternal) {
                plugin.internalOnLoad()
            } else plugin.onLoad()
        }.getOrElse {
            throw PluginLoadException("Exception while loading ${plugin.description.name}", it)
        }
    }

    override fun enable(plugin: JvmPlugin) {
        if (plugin.isEnabled) return
        ensureActive()
        if (plugin is JvmPluginInternal) {
            plugin.internalOnEnable()
        } else plugin.onEnable()
    }

    override fun disable(plugin: JvmPlugin) {
        if (!plugin.isEnabled) return
        ensureActive()

        if (plugin is JvmPluginInternal) {
            plugin.internalOnDisable()
        } else plugin.onDisable()
    }
}