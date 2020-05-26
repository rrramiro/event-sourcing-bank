package bank

import eu.timepit.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

package object model {
  type EmailPred   = MatchesRegex[W.`"""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$"""`.T]
  type StringEmail = String Refined EmailPred
}
