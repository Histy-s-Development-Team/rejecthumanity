package net.oraclehisty.rejecthumanity.assetgen

import java.io.File

/**
 * Regenerates the race models, animations and placeholder textures: `./gradlew generateAssets`.
 *
 * Generated files are overwritten when their content changes (unchanged files are left alone), so make lasting model
 * changes here, or stop generating a file once it is being hand-edited in Blockbench. Textures are placeholders
 * meant to be repainted; once one is, remove it from here.
 */
fun main(args: Array<String>) {
    val assets = File(args.singleOrNull() ?: error("Usage: AssetGen <assets/rejecthumanity dir>"))
    var total = 0
    var written = 0

    fun write(path: String, json: Any) {
        val file = File(assets, path)
        val text = Json.write(json)
        total++
        if (file.exists() && file.readText() == text) return
        file.parentFile.mkdirs()
        file.writeText(text)
        written++
    }

    fun save(dir: String, textures: Map<String, Canvas>) = textures.forEach { (name, canvas) ->
        total++
        if (canvas.save(File(assets, "textures/entity/$dir/$name.png"))) written++
    }

    for ((name, model) in ElfModels.all() + WerewolfModels.all()) {
        write("geo/entity/$name.geo.json", model.toJson())
        val animations = if (name.startsWith("werewolf")) WerewolfModels.animations else ElfModels.animations
        write("animations/entity/$name.animation.json", animations.toJson())
    }
    save("elf", ElfTextures.all())
    save("drow", DrowTextures.all())
    save("werewolf", WerewolfTextures.all())
    println("Generated $total asset files in $assets ($written changed)")
}
