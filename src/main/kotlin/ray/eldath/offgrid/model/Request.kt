package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.validator.routines.EmailValidator
import ray.eldath.offgrid.util.ErrorCodes

open class EmailRequest(@JsonIgnore open val emailAddress: String?) {

    open val email: String? by lazy {
        if (emailAddress != null && !EmailValidator.getInstance().isValid(emailAddress))
            throw ErrorCodes.INVALID_EMAIL_ADDRESS()
        emailAddress
    }
}