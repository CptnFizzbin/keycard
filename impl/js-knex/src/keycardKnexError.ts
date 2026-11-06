import { PolicyError } from "@cptn-fizzbin/keycard"

/** Thrown when a policy's conditions can't be translated to SQL for the query's dialect - an operator with no Knex translation, for example. */
export class KeycardKnexError extends PolicyError {
  constructor(message: string) {
    super(message)
    this.name = "KeycardKnexError"
  }
}
