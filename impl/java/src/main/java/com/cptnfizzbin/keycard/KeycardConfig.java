package com.cptnfizzbin.keycard;


import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.conditions.OperatorCatalog;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import lombok.Data;
import lombok.experimental.Accessors;

import java.lang.System.Logger;

@Data
@Accessors(fluent = true, chain = true)
public class KeycardConfig {
    private Logger logger = System.getLogger("Keycard");

    private ActionCatalog actions = new ActionCatalog();
    private Action anyAction = new Action("_ANY_");

    private SubjectCatalog subjects = new SubjectCatalog();
    private Subject<Object> anySubject = new Subject<>("_ANY_");

    private OperatorCatalog operators = new OperatorCatalog();
}
