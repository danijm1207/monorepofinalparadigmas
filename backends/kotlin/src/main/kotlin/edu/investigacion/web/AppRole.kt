package edu.investigacion.web

import edu.investigacion.domain.Role
import io.javalin.security.RouteRole

enum class AppRole : RouteRole {
    ANYONE,
    AUTHENTICATED,
    ADMIN
}
