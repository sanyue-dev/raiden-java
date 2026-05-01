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

  @NotNull
  public static CheckedDisposable newCheckedDisposable() {
    return new CheckedDisposableImpl();
  }

  @NotNull
  public static CheckedDisposable newCheckedDisposable(@NotNull String debugName) {
    return new NamedCheckedDisposable(debugName);
  }

  @NotNull
  public static CheckedDisposable newCheckedDisposable(@NotNull Disposable parentDisposable) {
    CheckedDisposable disposable = newCheckedDisposable();
    register(parentDisposable, disposable);
    return disposable;
  }

  @NotNull
  public static CheckedDisposable newCheckedDisposable(@NotNull Disposable parentDisposable, @NotNull String debugName) {
    CheckedDisposable disposable = newCheckedDisposable(debugName);
    register(parentDisposable, disposable);
    return disposable;
  }

  public static void register(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) {
      throw new IllegalArgumentException("Cannot register disposable to itself: " + parent);
    }
    synchronized (TREE_LOCK) {
      if (isDisposed(parent)) {
        throw new IllegalStateException("Parent has already been disposed: " + parent +
                                        ", cannot register child: " + child);
      }

      // Re-registering a disposed CheckedDisposable is likely a bug
      if (child instanceof CheckedDisposableImpl && ((CheckedDisposableImpl) child).isDisposed) {
        throw new IllegalStateException("Cannot re-register a disposed CheckedDisposable: " + child);
      }

      Node parentNode = ourObject2NodeMap.computeIfAbsent(parent, Node::new);
      Node childNode = ourObject2NodeMap.get(child);
      if (childNode != null) {
        // Remove from old parent first (even if same parent, to avoid duplicates)
        if (childNode.myParent != null) {
          childNode.myParent.myChildren.remove(childNode);
        }
        checkNotAncestor(childNode, parentNode);
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

  public static boolean tryRegister(@NotNull Disposable parent, @NotNull Disposable child) {
    if (parent == child) return false;
    synchronized (TREE_LOCK) {
      if (isDisposed(parent)) {
        return false;
      }
      Node parentNode = ourObject2NodeMap.computeIfAbsent(parent, Node::new);
      Node childNode = ourObject2NodeMap.get(child);
      if (childNode != null && childNode.myParent == parentNode) {
        return true;
      }
      if (childNode != null) {
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
      return true;
    }
  }

  public static void dispose(@NotNull Disposable disposable) {
    List<Disposable> disposables;
    synchronized (TREE_LOCK) {
      Node node = ourObject2NodeMap.remove(disposable);
      if (node == null) {
        disposables = null;
      }
      else {
        if (node.myParent != null) {
          node.myParent.myChildren.remove(node);
        }
        disposables = new ArrayList<>();
        collectAndRemoveNodes(node, disposables);
      }
    }
    if (disposables == null) {
      disposable.dispose();
      return;
    }

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

  public static boolean isDisposed(@NotNull Disposable disposable) {
    if (disposable instanceof CheckedDisposable) {
      return ((CheckedDisposable) disposable).isDisposed();
    }
    synchronized (TREE_LOCK) {
      Node node = ourObject2NodeMap.get(disposable);
      return node != null && node.myDisposed;
    }
  }

  /**
   * Checks that {@code descendant} is not an ancestor of {@code node},
   * which would create a cycle in the tree.
   */
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
    node.myDisposed = true;
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
    boolean myDisposed;

    Node(@NotNull Disposable disposable) {
      myDisposable = disposable;
    }
  }

  static class CheckedDisposableImpl implements CheckedDisposable {
    volatile boolean isDisposed;

    @Override
    public boolean isDisposed() {
      return isDisposed;
    }

    @Override
    public void dispose() {
      isDisposed = true;
    }

    @Override
    public String toString() {
      return "CheckedDisposable{isDisposed=" + isDisposed + "}";
    }
  }

  private static final class NamedCheckedDisposable extends CheckedDisposableImpl {
    private final String myDebugName;

    NamedCheckedDisposable(@NotNull String debugName) {
      myDebugName = debugName;
    }

    @Override
    public String toString() {
      return myDebugName + "{isDisposed=" + isDisposed + "}";
    }
  }
}
