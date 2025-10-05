package me.javierflores.bettergolems;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Optional;

public final class ReflectiveHandler {

    private ReflectiveHandler() {}

    private static Optional<Class<?>> findCraftBukkitClass(String className) {
        try {
            String craftbukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
            return Optional.of(Class.forName(craftbukkitPackage + "." + className));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static FieldBuilder ofField(String fieldName, Class<?> owner, Class<?> fieldType) {
        return new FieldBuilder(owner, fieldName, fieldType);
    }

    public static MethodBuilder ofMethod(String methodName) {
        return new MethodBuilder(methodName);
    }

    public static final class FieldBuilder {
        private final Class<?> owner;
        private final String fieldName;
        private final Class<?> fieldType;

        private FieldBuilder(Class<?> owner, String fieldName, Class<?> fieldType) {
            this.owner = owner;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
        }

        public Optional<VarHandle> privileged() {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                return Optional.of(lookup.findVarHandle(owner, fieldName, fieldType));
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }

        public Optional<VarHandle> get() {
            try {
                return Optional.of(MethodHandles.lookup().findVarHandle(owner, fieldName, fieldType));
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    public static final class MethodBuilder {
        private final String methodName;
        private Class<?> owner;
        private MethodType methodType;

        private MethodBuilder(String methodName) {
            this.methodName = methodName;
        }

        public MethodBuilder from(Class<?> owner) {
            this.owner = owner;
            return this;
        }

        public MethodBuilder fromCraftClass(String className) {
            this.owner = ReflectiveHandler.findCraftBukkitClass(className).orElse(null);
            return this;
        }

        public MethodBuilder withType(MethodType methodType) {
            this.methodType = methodType;
            return this;
        }

        public Optional<MethodHandle> get() {
            if (owner == null || methodType == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(MethodHandles.lookup().findVirtual(owner, methodName, methodType));
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }
}