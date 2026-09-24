package com.cptnfizzbin.keycard.examples.vision.realbackend;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;

public class AppActions {
    // dynamic: no name baked into the Action itself - catalog.set(...)'s
    // key is what actually gets serialized into a PolicyDefinition's rule
    // tuples, and it registers the Action the same moment it names it
    public static final ActionCatalog catalog = new ActionCatalog();

    public static Action Create = catalog.set("create", new Action());
    public static Action Read = catalog.set("read", new Action());
    public static Action Update = catalog.set("update", new Action());
    public static Action Delete = catalog.set("delete", new Action());
    public static Action Invite = catalog.set("invite", new Action());
}
