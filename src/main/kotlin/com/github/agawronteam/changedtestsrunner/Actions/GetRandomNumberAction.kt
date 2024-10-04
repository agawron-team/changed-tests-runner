import com.github.agawronteam.changedtestsrunner.MyBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.github.agawronteam.changedtestsrunner.services.MyProjectService
import com.intellij.collaboration.util.serviceGet
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

class GetRandomNumberAction : AnAction(), DumbAware {

    init {
        //thisLogger().warn("My own action!")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<MyProjectService>()
        val randomNumber = service.getRandomNumber()
        service.thisLogger().info(MyBundle.message("randomNumberGenerated", randomNumber))
    }
}