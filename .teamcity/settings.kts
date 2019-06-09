import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2019.1"

project {

    buildType(Build)
    buildType(BuildAndPackage)

    params {
        param("env.NEXUS_USERNAME", "admin")
        password("env.NEXUS_PASSWORD", "credentialsJSON:72b5d0af-8e0e-4b87-8673-5e3b1c0de65c", label = "NEXUS_Credentials", display = ParameterDisplay.PROMPT, readOnly = true)
    }
}

object Build : BuildType({
    name = "Build"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "Test"
            scriptContent = """
                yarn install
                yarn test
            """.trimIndent()
            dockerImage = "node:latest"
            dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
            dockerPull = true
        }
    }

    triggers {
        vcs {
        }
    }
})

object BuildAndPackage : BuildType({
    name = "Build And Package"
    description = "Build and package the app"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "build_package"
            scriptContent = """
                yarn install
                yarn test
                echo ${'$'}NEXUS_USERNAME:${'$'}NEXUS_PASSWORD
                yarn preelectron-pack
                yarn electron:pack
                EXE_FILE=${'$'}(ls dist/ | grep ".exe${'$'}")
                echo "uploading exe file with name: ${'$'}EXE_FILE"
                [[ ! -z "${'$'}EXE_FILE" ]] && curl -v -u ${'$'}NEXUS_USERNAME:${'$'}NEXUS_PASSWORD --upload-file dist/"${'$'}EXE_FILE" http://localhost:8081/repository/bundle/pricer/store-app/manager/
                DEB_FILE=${'$'}(ls dist/ | grep ".deb${'$'}")
                echo "uploading dmg file with name: ${'$'}DEB_FILE"
                [[ ! -z "${'$'}DEB_FILE" ]] && curl -v -u ${'$'}NEXUS_USERNAME:${'$'}NEXUS_PASSWORD --upload-file dist/"${'$'}DEB_FILE" http://localhost:8081/repository/bundle/pricer/store-app/manager/
            """.trimIndent()
            dockerImage = "electronuserland/builder:wine"
            dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
            dockerPull = true
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "${Build.id}"
            successfulOnly = true
        }
    }
})
