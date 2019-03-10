package com.worldturner.medeia.schema.performance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.worldturner.medeia.api.PathSchemaSource
import com.worldturner.medeia.api.ValidationFailedException
import com.worldturner.medeia.api.gson.MedeiaGsonApi
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi
import com.worldturner.medeia.parser.gson.GsonJsonReaderDecorator
import com.worldturner.medeia.parser.type.MapperType
import com.worldturner.medeia.schema.parser.JsonSchemaDraft04Type
import com.worldturner.medeia.schema.validation.stream.SchemaValidatingConsumer
import org.everit.json.schema.ValidationException
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

abstract class PerformanceTest(val iterations: Int) {

    abstract fun run(): Boolean

    fun runWithTiming(): Double =
        timing {
            if (!run()) {
                println("Validation failed for $this")
            }
        }
}

class JsonNodeValidatorPerformanceTest(schemaPath: Path, dataPath: Path, iterations: Int) :
    PerformanceTest(iterations) {
    val schemaTree = Files.newBufferedReader(schemaPath).use { testMapper.readTree(it) }
    val factory = JsonSchemaFactory.byDefault()
    val schema = factory.getJsonSchema(schemaTree)
    val data = String(Files.readAllBytes(dataPath), Charsets.UTF_8)

    override fun run(): Boolean {
        val dataTree = testMapper.readTree(data)
        val report: ProcessingReport = schema.validate(dataTree)
        return report.isSuccess
    }

    companion object {
        internal val testMapper = ObjectMapper()
    }
}

class MedeiaJacksonPerformanceTest(
    schemaPath: Path,
    dataPath: Path,
    iterations: Int
) : PerformanceTest(iterations) {
    val api = MedeiaJacksonApi()
    val validator = api.loadSchema(PathSchemaSource(schemaPath))
    val data = String(Files.readAllBytes(dataPath), Charsets.UTF_8)

    override fun run(): Boolean {
        val parser = api.decorateJsonParser(validator, api.jsonFactory.createParser(data))
        return try {
            while (parser.nextToken() != null) {
            }
            true
        } catch (e: ValidationFailedException) {
            println("Exception: $e")
            false
        }
    }
}

class MedeiaGsonPerformanceTest(
    schemaPath: Path,
    dataPath: Path,
    iterations: Int,
    schemaType: MapperType = JsonSchemaDraft04Type
) : PerformanceTest(iterations) {
    val api = MedeiaGsonApi()
    val validator = api.loadSchema(PathSchemaSource(schemaPath))
    val data = String(Files.readAllBytes(dataPath), Charsets.UTF_8)

    override fun run(): Boolean {
        val consumer = SchemaValidatingConsumer(validator)
        val parser = GsonJsonReaderDecorator(StringReader(data), consumer)
        return try {
            parser.parseAll()
            true
        } catch (e: ValidationFailedException) {
            println("Exception: $e")
            false
        }
    }
}

class EveritPerformanceTest(schemaPath: Path, dataPath: Path, iterations: Int) : PerformanceTest(iterations) {
    val schemaTree = Files.newBufferedReader(schemaPath).use {
        JSONObject(JSONTokener(it))
    }
    val schema = SchemaLoader.load(schemaTree)
    val data = String(Files.readAllBytes(dataPath), Charsets.UTF_8)

    override fun run(): Boolean {
        val jsonObject = JSONObject(JSONTokener(data))
        return try {
            schema.validate(jsonObject)
            true
        } catch (e: ValidationException) {
            println("Validation Exception for ${this::class.java.simpleName}: $e")
            false
        }
    }
}