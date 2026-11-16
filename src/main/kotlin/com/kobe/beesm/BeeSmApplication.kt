package com.kobe.beesm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.ReactiveUserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [ReactiveUserDetailsServiceAutoConfiguration::class])
class BeeSmApplication

fun main(args: Array<String>) {
	runApplication<BeeSmApplication>(*args)
}


















