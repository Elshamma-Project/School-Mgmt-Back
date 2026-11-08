package com.kobe.smels

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [ReactiveUserDetailsServiceAutoConfiguration::class])
class SmelsApplication

fun main(args: Array<String>) {
	runApplication<SmelsApplication>(*args)
}
