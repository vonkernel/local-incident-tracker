package com.vonkernel.lit.indexer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.vonkernel.lit"])
class IndexerApplication

fun main(args: Array<String>) {
    runApplication<IndexerApplication>(*args)
}