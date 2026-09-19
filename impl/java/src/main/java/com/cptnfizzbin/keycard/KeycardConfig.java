package com.cptnfizzbin.keycard;


import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;
import com.cptnfizzbin.keycard.subject.Subject;
import com.cptnfizzbin.keycard.subject.SubjectCatalog;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.lang.System.Logger;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class KeycardConfig {
    private ActionCatalog actions = new ActionCatalog();
    private SubjectCatalog subjects = new SubjectCatalog();
    private Action anyAction = new Action("_ANY_");
    private Subject<Object> anySubject = new Subject<>("_ANY_");
    private Logger logger = System.getLogger("Keycard");
}
