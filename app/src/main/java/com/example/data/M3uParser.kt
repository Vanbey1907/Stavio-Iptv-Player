package com.example.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

object M3uParser {
    fun parse(m3uContent: String, playlistId: Long): List<ChannelEntity> {
        val reader = BufferedReader(StringReader(m3uContent))
        return parseInternal(reader, playlistId)
    }

    fun parse(inputStream: InputStream, playlistId: Long): List<ChannelEntity> {
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        return parseInternal(reader, playlistId)
    }

    private fun parseInternal(reader: BufferedReader, playlistId: Long): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        var line: String? = reader.readLine()
        
        var currentExtInf: String? = null
        var currentExtGrp: String? = null
        
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                line = reader.readLine()
                continue
            }
            
            if (trimmed.startsWith("#EXTINF:")) {
                currentExtInf = trimmed
                currentExtGrp = null // reset for new channel
            } else if (trimmed.startsWith("#EXTGRP:")) {
                currentExtGrp = trimmed.substringAfter("#EXTGRP:").trim()
            } else if (trimmed.startsWith("#")) {
                // Ignore additional headers
            } else {
                if (currentExtInf != null) {
                    val url = trimmed
                    val name = parseName(currentExtInf)
                    val logoUrl = parseAttribute(currentExtInf, "tvg-logo")
                    var category = parseAttribute(currentExtInf, "group-title")
                    
                    if (category.isEmpty() && currentExtGrp != null && currentExtGrp.isNotEmpty()) {
                        category = currentExtGrp
                    }
                    if (category.isEmpty()) {
                        category = "Diğer"
                    }
                    
                    val tvgId = parseAttribute(currentExtInf, "tvg-id").ifEmpty { null }
                    val tvgUrl = parseAttribute(currentExtInf, "tvg-url").ifEmpty { null }
                    
                    channels.add(
                        ChannelEntity(
                            playlistId = playlistId,
                            url = url,
                            name = name,
                            groupTitle = category,
                            logoUrl = logoUrl,
                            tvgId = tvgId,
                            tvgUrl = tvgUrl,
                            isFavorite = false
                        )
                    )
                    currentExtInf = null
                    currentExtGrp = null
                }
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun parseName(extInfLine: String): String {
        val commaIndex = extInfLine.lastIndexOf(',')
        if (commaIndex != -1 && commaIndex < extInfLine.length - 1) {
            return extInfLine.substring(commaIndex + 1).trim()
        }
        return "Kanal"
    }

    private fun parseAttribute(extInfLine: String, attributeName: String): String {
        val key = "$attributeName="
        val index = extInfLine.indexOf(key)
        if (index == -1) return ""
        
        val startOfValue = index + key.length
        if (startOfValue >= extInfLine.length) return ""
        
        val firstChar = extInfLine[startOfValue]
        return if (firstChar == '"') {
            val endQuoteIndex = extInfLine.indexOf('"', startOfValue + 1)
            if (endQuoteIndex != -1) {
                extInfLine.substring(startOfValue + 1, endQuoteIndex)
            } else {
                extInfLine.substring(startOfValue + 1)
            }
        } else {
            var endIndex = extInfLine.indexOf(' ', startOfValue)
            val commaIndex = extInfLine.indexOf(',', startOfValue)
            if (endIndex == -1 || (commaIndex != -1 && commaIndex < endIndex)) {
                endIndex = commaIndex
            }
            if (endIndex != -1) {
                extInfLine.substring(startOfValue, endIndex)
            } else {
                extInfLine.substring(startOfValue)
            }
        }.trim()
    }
}
