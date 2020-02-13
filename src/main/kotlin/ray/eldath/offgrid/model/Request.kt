package ray.eldath.offgrid.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.apache.commons.validator.routines.EmailValidator
import ray.eldath.offgrid.util.ErrorCodes

open class EmailRequest(@JsonIgnore val emailAddress: String?) {

    open val email: String? = "" // backward capability, useless

    init {
        if (emailAddress != null && !EmailValidator.getInstance().isValid(emailAddress))
            throw ErrorCodes.INVALID_EMAIL_ADDRESS() // will hide by contract with `Invalid Parameters`, no workaround currently :-(
    }
}