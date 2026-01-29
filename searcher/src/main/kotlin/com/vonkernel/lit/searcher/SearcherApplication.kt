package com.vonkernel.lit.searcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.vonkernel.lit"])
class SearcherApplication

fun main(args: Array<String>) {
    runApplication<SearcherApplication>(*args)
}