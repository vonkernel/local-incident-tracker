package com.vonkernel.lit.analyzer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.vonkernel.lit"])
class AnalyzerApplication

fun main(args: Array<String>) {
    runApplication<AnalyzerApplication>(*args)
}