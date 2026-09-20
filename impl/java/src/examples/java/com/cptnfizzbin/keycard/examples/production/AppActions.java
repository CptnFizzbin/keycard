package com.cptnfizzbin.keycard.examples.production;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionCatalog;

import java.util.List;

public class AppActions {
    public static Action Create = new Action();
    public static Action Read = new Action();
    public static Action Update = new Action();
    public static Action Delete = new Action();

    public static List<Action> CreateAndAccess = List.of(Create, Read);
    public static List<Action> Manage = List.of(Create, Read, Update, Delete);

    public static ActionCatalog getCatalog() {
        return new ActionCatalog()
            .add("create", Create)
            .add("read", Read)
            .add("update", Update)
            .add("delete", Delete);
    }
}
