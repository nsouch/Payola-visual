package cz.payola.web.shared.managers

import scala.collection.mutable
import cz.payola.common.entities.User
import cz.payola.web.shared._
import cz.payola.common.PayolaException
import cz.payola.web.shared.Email
import s2js.compiler.remote
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global 
import akka.actor.ActorSystem

@remote
object PasswordManager
{
    // On a besoin d'un ActorSystem pour le scheduler
    // Dans Play 2.6, il est préférable de l'injecter, mais pour un objet singleton :
    private val system = ActorSystem("PasswordRecoverySystem")

    lazy private val recoveryHashMap: mutable.HashMap[String, (String, String)] = new mutable.HashMap[String, (String, String)]()

    @remote def sendRecoveryEmailToUser(uuid: String, user: User, newPassword: String) {
        recoveryHashMap.put(uuid, (user.id, newPassword))
        system.scheduler.scheduleOnce(2.hours) {
            recoveryHashMap.remove(uuid)
        }

        val content =
            """
              |Hello,
              |
              |please, follow this link to confirm your password reset:
              |
              |%s/reset/%s
            """.stripMargin.format(Payola.settings.websiteURL, uuid)

        val email = new Email("Reset Your Payola Password", content, Payola.settings.websiteNoReplyEmail, List(user.email))
        email.send()
    }

    @remote def confirmPasswordReset(uuid: String): Boolean = {
        if (recoveryHashMap.contains(uuid)){
            val tup = recoveryHashMap.get(uuid).get

            val userOpt = Payola.model.userModel.getById(tup._1)
            if (userOpt.isEmpty){
                throw new PayolaException("User disappeared.")
            }

            Payola.model.userModel.changePasswordForUser(userOpt.get, tup._2)
            Payola.model.userModel.persist(userOpt.get)

            recoveryHashMap.remove(uuid)
            true
        }else{
            false
        }
    }

}
