package com.vonkernel.lit.collector

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["com.vonkernel.lit"])
class CollectorApplication

fun main(args: Array<String>) {
    runApplication<CollectorApplication>(*args)
}
