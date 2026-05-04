package com.raiden.platform;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class Disposer {

  private Disposer() {}

  private static final Object TREE_LOCK = new Object();
  private static final Map<Disposable, Node> ourObject2NodeMap = new IdentityHashMap<>();
  private static final Map<Disposable, Boolean> ourDisposingObjects = new IdentityHashMap<>();

  @NotNull
  public static Disposable newDisposable() {
    return newDisposable("newDisposable");
  }

  @NotNull
  public static Disposable newDisposable(@NotNull String debugName) {
    return new Disposable() {
      @Override
      public void dispose() {}

      @Override
      public String toString() {
        return debugName;
      }
    };
  }

  @NotNull
  public static Disposable newDisposable(@NotNull Disposable parentDisposable) {
    Disposable disposable = newDisposable();
    register(parentDisposable, disposable);
    return disposable;
  }

  @NotNull
  public static Disposable newDisposable(@NotNull Disposable parentDisposable, @NotNull String debugName) {
    Disposable result = newDisposable(debugName);
    register(parentDisposable, result);
    return result;
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) {
      throw new IllegalArgumentException("Cannot register disposable to itself: " + parent);
    }
    synchronized (TREE_LOCK) {
      if (ourDisposingObjects.containsKey(parent)) {
        throw new IllegalStateException("Parent is being disposed: " + parent +
                                        ", cannot register child: " + child);
      }

      Node parentNode = ourObject2NodeMap.computeIfAbsent(parent, Node::new);
      Node childNode = ourObject2NodeMap.get(child);
      if (childNode != null) {
        if (childNode.myParent == parentNode) {
          return;
        }
        checkNotAncestor(childNode, parentNode);
        if (childNode.myParent != null) {
          childNode.myParent.myChildren.remove(childNode);
        }
        childNode.myParent = parentNode;
      }
      else {
        childNode = new Node(child);
        childNode.myParent = parentNode;
        ourObject2NodeMap.put(child, childNode);
      }
      parentNode.myChildren.add(childNode);
    }
  }

  public static void dispose(@NotNull Disposable disposable) {
    List<Disposable> disposables;
    synchronized (TREE_LOCK) {
      Node node = ourObject2NodeMap.remove(disposable);
      if (node == null) {
        disposables = List.of(disposable);
      }
      else {
        if (node.myParent != null) {
          node.myParent.myChildren.remove(node);
        }
        disposables = new ArrayList<>();
        collectAndRemoveNodes(node, disposables);
      }
      for (Disposable d : disposables) {
        ourDisposingObjects.put(d, Boolean.TRUE);
      }
    }

    try {
      disposeAll(disposables);
    }
    finally {
      synchronized (TREE_LOCK) {
        for (Disposable d : disposables) {
          ourDisposingObjects.remove(d);
        }
      }
    }
  }

  private static void disposeAll(@NotNull List<Disposable> disposables) {
    List<Throwable> exceptions = null;
    for (int i = disposables.size() - 1; i >= 0; i--) {
      Disposable d = disposables.get(i);
      if (d instanceof Disposable.Parent) {
        try {
          ((Disposable.Parent) d).beforeTreeDispose();
        }
        catch (Throwable t) {
          if (exceptions == null) exceptions = new ArrayList<>();
          exceptions.add(t);
        }
      }
    }
    for (Disposable d : disposables) {
      try {
        d.dispose();
      }
      catch (Throwable t) {
        if (exceptions == null) exceptions = new ArrayList<>();
        exceptions.add(t);
      }
    }
    if (exceptions != null && !exceptions.isEmpty()) {
      Throwable first = exceptions.get(0);
      for (int i = 1; i < exceptions.size(); i++) {
        first.addSuppressed(exceptions.get(i));
      }
      if (first instanceof RuntimeException) throw (RuntimeException) first;
      throw new RuntimeException(first);
    }
  }

  private static void checkNotAncestor(@NotNull Node descendant, @NotNull Node node) {
    for (Node current = node; current != null; current = current.myParent) {
      if (current == descendant) {
        throw new IllegalStateException(
            "'" + descendant.myDisposable + "' is already an ancestor of '" +
            node.myDisposable + "', registering would create a cycle");
      }
    }
  }

  private static void collectAndRemoveNodes(@NotNull Node node, @NotNull List<Disposable> result) {
    List<Node> children = node.myChildren;
    for (int i = children.size() - 1; i >= 0; i--) {
      Node child = children.get(i);
      ourObject2NodeMap.remove(child.myDisposable);
      collectAndRemoveNodes(child, result);
    }
    children.clear();
    result.add(node.myDisposable);
  }

  private static final class Node {
    final Disposable myDisposable;
    Node myParent;
    final List<Node> myChildren = new ArrayList<>();

    Node(@NotNull Disposable disposable) {
      myDisposable = disposable;
    }
  }
}
