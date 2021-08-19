package com.github.anstarovoyt.intellijstimulus.services

import com.github.anstarovoyt.intellijstimulus.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
