package com.kobe.smels

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SmelsApplication

fun main(args: Array<String>) {
	runApplication<SmelsApplication>(*args)
}
