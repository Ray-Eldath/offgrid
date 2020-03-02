package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.validator.routines.EmailValidator
import ray.eldath.offgrid.util.ErrorCodes

open class EmailRequest(@JsonIgnore private val emailAddress: String?) {

    fun check() {
        if (emailAddress != null && !EmailValidator.getInstance().isValid(emailAddress))
            throw ErrorCodes.INVALID_EMAIL_ADDRESS()
    }
}