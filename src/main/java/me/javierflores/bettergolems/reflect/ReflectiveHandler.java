package me.javierflores.bettergolems.reflect;

import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This class simplifies working with {@link MethodHandle} and {@link VarHandle}
 * with support for mappings and cross-version compatibility.
 */
public final class ReflectiveHandler {

    private static MappingResolver resolver = new DirectMappingResolver();
    private static MethodHandles.Lookup defaultLookup = MethodHandles.lookup();
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();
    private static boolean cachingEnabled = true;

    private ReflectiveHandler() {
        // Utility class
    }

    /**
     * Sets the mapping resolver to use for all reflective operations.
     *
     * @param resolver The mapping resolver implementation.
     */
    public static void setMappingResolver(MappingResolver resolver) {
        ReflectiveHandler.resolver = resolver;
        cache.clear(); // Clear cache when resolver changes
    }

    /**
     * Sets the default lookup to use for all reflective operations.
     * This is useful when you need a lookup with specific privileges.
     *
     * @param lookup The MethodHandles.Lookup to use.
     */
    public static void setDefaultLookup(MethodHandles.Lookup lookup) {
        ReflectiveHandler.defaultLookup = lookup;
        cache.clear(); // Clear cache when lookup changes
    }

    /**
     * Gets the current default lookup.
     *
     * @return The current default lookup.
     */
    public static MethodHandles.Lookup getDefaultLookup() {
        return defaultLookup;
    }

    /**
     * Enables or disables caching of reflective lookups.
     *
     * @param enabled Whether caching should be enabled.
     */
    public static void setCachingEnabled(boolean enabled) {
        cachingEnabled = enabled;
        if (!enabled) {
            cache.clear();
        }
    }

    /**
     * Clears the reflection cache.
     */
    public static void clearCache() {
        cache.clear();
    }

    private static Class<?> findCraftBukkitClass(String className) throws ClassNotFoundException, IllegalAccessException {
        String craftbukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
        String fullClassName = craftbukkitPackage + "." + className;
        return defaultLookup.findClass(fullClassName);
    }

    /**
     * Starts building a {@link VarHandle} lookup.
     *
     * @param fieldName The name of the field.
     * @return A {@link FieldBuilder} to continue the lookup configuration.
     */
    public static FieldBuilder ofField(String fieldName) {
        return new FieldBuilder(fieldName);
    }

    /**
     * Starts building a {@link MethodHandle} lookup.
     *
     * @param methodName The name of the method.
     * @return A {@link MethodBuilder} to continue the lookup configuration.
     */
    public static MethodBuilder ofMethod(String methodName) {
        return new MethodBuilder(methodName);
    }

    /**
     * Starts building a {@link MethodHandle} lookup for a constructor.
     *
     * @return A {@link ConstructorBuilder} to continue the lookup configuration.
     */
    public static ConstructorBuilder ofConstructor() {
        return new ConstructorBuilder();
    }

    /**
     * An abstract base class for reflective builders.
     *
     * @param <B> The concrete builder subclass.
     * @param <T> The type of handle being built ({@link VarHandle} or {@link MethodHandle}).
     */
    public static abstract class ReflectiveBuilder<B extends ReflectiveBuilder<B, T>, T> {
        protected Class<?> owner;
        protected boolean isPrivileged = false;
        protected boolean isStatic = false;
        protected Class<?> type;
        protected ReflectiveOperationException lastException;

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        /**
         * Specifies the owner class for the lookup.
         *
         * @param owner The class containing the member.
         * @return This builder instance for chaining.
         */
        public B from(Class<?> owner) {
            this.owner = owner;
            return self();
        }

        /**
         * Specifies the owner class for the lookup using a CraftBukkit class name.
         *
         * @param className The simple name of the class within the CraftBukkit package.
         * @return This builder instance for chaining.
         */
        public B fromCraftClass(String className) {
            try {
                this.owner = findCraftBukkitClass(className);
            } catch (ClassNotFoundException | IllegalAccessException e) {
                this.owner = null;
                this.lastException = e;
            }
            return self();
        }

        /**
         * Marks the lookup to be performed with privileged access.
         *
         * @return This builder instance for chaining.
         */
        public B privileged() {
            this.isPrivileged = true;
            return self();
        }

        /**
         * Marks the member as static.
         *
         * @return This builder instance for chaining.
         */
        public B staticAccess() {
            this.isStatic = true;
            return self();
        }

        /**
         * Specifies the type of the member. For fields, this is the field type.
         * For methods, this is the return type.
         *
         * @param type The member's type.
         * @return This builder instance for chaining.
         */
        public B type(Class<?> type) {
            this.type = type;
            return self();
        }

        /**
         * Specifies the type of the member using a CraftBukkit class name.
         *
         * @param className The simple name of the class within the CraftBukkit package.
         * @return This builder instance for chaining.
         */
        public B craftType(String className) {
            try {
                this.type = findCraftBukkitClass(className);
            } catch (ClassNotFoundException | IllegalAccessException e) {
                this.type = null;
                this.lastException = e;
            }
            return self();
        }

        /**
         * Attempts the lookup and returns an Optional containing the handle if successful.
         *
         * @return An Optional containing the handle, or empty if the lookup fails.
         */
        public abstract Optional<T> find();

        /**
         * Performs the lookup and returns the handle, or throws a custom exception on failure.
         *
         * @param message The exception message to use if the lookup fails.
         * @return The found handle.
         * @throws ReflectiveException if the lookup fails.
         */
        public T expect(String message) {
            var fullMessage = message + " (" + getDetailsString() + ")";
            return find().orElseThrow(() -> new ReflectiveException(fullMessage,
                    Objects.requireNonNullElse(lastException, new IllegalStateException("Owner class or type was not found or set."))));
        }

        protected abstract String getDetailsString();
    }

    /**
     * A builder for constructing a {@link VarHandle}.
     */
    public static final class FieldBuilder extends ReflectiveBuilder<FieldBuilder, VarHandle> {
        private final String fieldName;
        private FieldAccessType accessType = FieldAccessType.READ_WRITE;

        private FieldBuilder(String fieldName) {
            this.fieldName = fieldName;
        }

        /**
         * Specifies the access type for this field handle.
         *
         * @param accessType The desired access type.
         * @return This builder instance for chaining.
         */
        public FieldBuilder access(FieldAccessType accessType) {
            this.accessType = accessType;
            return this;
        }

        @Override
        protected String getDetailsString() {
            return "field: " + fieldName + ", owner: " +
                    (owner != null ? owner.getName() : "null") +
                    ", type: " + (type != null ? type.getName() : "null");
        }

        @Override
        public Optional<VarHandle> find() {
            lastException = null;

            if (owner == null || type == null) {
                return Optional.empty();
            }

            String cacheKey = buildCacheKey();
            if (cachingEnabled && cache.containsKey(cacheKey)) {
                return Optional.of((VarHandle) cache.get(cacheKey));
            }

            try {
                String resolvedFieldName = resolver.resolveFieldName(owner, fieldName);
                MethodHandles.Lookup lookup = isPrivileged
                        ? MethodHandles.privateLookupIn(owner, defaultLookup)
                        : defaultLookup;

                VarHandle handle = isStatic
                        ? lookup.findStaticVarHandle(owner, resolvedFieldName, type)
                        : lookup.findVarHandle(owner, resolvedFieldName, type);

                if (cachingEnabled) {
                    cache.put(cacheKey, handle);
                }
                return Optional.of(handle);
            } catch (ReflectiveOperationException e) {
                lastException = e;
                return Optional.empty();
            }
        }

        private String buildCacheKey() {
            return "field:" + owner.getName() + "#" + fieldName +
                    ":" + type.getName() + ":" + isStatic + ":" + accessType;
        }
    }

    /**
     * A builder for constructing a {@link MethodHandle}.
     */
    public static final class MethodBuilder extends ReflectiveBuilder<MethodBuilder, MethodHandle> {
        private final String methodName;
        private Class<?>[] paramTypes = new Class<?>[0];
        private MethodLookupType lookupType = MethodLookupType.VIRTUAL;

        private MethodBuilder(String methodName) {
            this.methodName = methodName;
        }

        /**
         * Specifies the parameter types for the method lookup.
         *
         * @param paramTypes The parameter types of the method.
         * @return This builder instance for chaining.
         */
        public MethodBuilder withParams(Class<?>... paramTypes) {
            this.paramTypes = paramTypes;
            return this;
        }

        /**
         * Marks this method lookup as static.
         * Note: This overrides the parent's asStatic() to also set the lookup type.
         *
         * @return This builder instance for chaining.
         */
        @Override
        public MethodBuilder staticAccess() {
            super.staticAccess();
            this.lookupType = MethodLookupType.STATIC;
            return this;
        }

        /**
         * Marks this method lookup as special (super method invocation).
         *
         * @return This builder instance for chaining.
         */
        public MethodBuilder specialAccess() {
            this.lookupType = MethodLookupType.SPECIAL;
            return this;
        }

        @Override
        protected String getDetailsString() {
            String params = Arrays.stream(paramTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "));
            return "method: %s(%s), owner: %s, return type: %s".formatted(
                    methodName,
                    params,
                    owner != null ? owner.getName() : "null",
                    type != null ? type.getName() : "null");
        }

        @Override
        public Optional<MethodHandle> find() {
            lastException = null;

            if (owner == null || type == null) {
                return Optional.empty();
            }

            String cacheKey = buildCacheKey();
            if (cachingEnabled && cache.containsKey(cacheKey)) {
                return Optional.of((MethodHandle) cache.get(cacheKey));
            }

            try {
                String resolvedMethodName = resolver.resolveMethodName(owner, methodName);
                MethodType methodType = MethodType.methodType(type, paramTypes);
                MethodHandles.Lookup lookup = isPrivileged
                        ? MethodHandles.privateLookupIn(owner, defaultLookup)
                        : defaultLookup;

                MethodHandle handle = switch (lookupType) {
                    case VIRTUAL -> lookup.findVirtual(owner, resolvedMethodName, methodType);
                    case STATIC -> lookup.findStatic(owner, resolvedMethodName, methodType);
                    case SPECIAL -> lookup.findSpecial(owner, resolvedMethodName, methodType, owner);
                };

                if (cachingEnabled) {
                    cache.put(cacheKey, handle);
                }
                return Optional.of(handle);
            } catch (ReflectiveOperationException e) {
                lastException = e;
                return Optional.empty();
            }
        }

        private String buildCacheKey() {
            return Arrays.stream(paramTypes)
                    .map(param -> param.getName() + ",")
                    .collect(Collectors.joining("",
                            "method:" + owner.getName() + "#" + methodName + ":" + type.getName() + ":",
                            ":" + lookupType));
        }
    }

    /**
     * A builder for constructing a {@link MethodHandle} for constructors.
     * Note: The type() method is not applicable for constructors as they always
     * return an instance of the owner class.
     */
    public static final class ConstructorBuilder extends ReflectiveBuilder<ConstructorBuilder, MethodHandle> {
        private Class<?>[] paramTypes = new Class<?>[0];

        private ConstructorBuilder() {
        }

        /**
         * Specifies the parameter types for the constructor.
         *
         * @param paramTypes The parameter types of the constructor.
         * @return This builder instance for chaining.
         */
        public ConstructorBuilder withParams(Class<?>... paramTypes) {
            this.paramTypes = paramTypes;
            return this;
        }

        /**
         * Not applicable for constructors. Constructors always return an instance of the owner class.
         * This method is inherited but should not be used.
         *
         * @throws UnsupportedOperationException always
         * @deprecated Constructors don't have a separate return type
         */
        @Deprecated
        @Override
        public ConstructorBuilder type(Class<?> type) {
            throw new UnsupportedOperationException("Constructors don't have a separate return type. Use from() to set the class.");
        }

        /**
         * Not applicable for constructors. Constructors always return an instance of the owner class.
         * This method is inherited but should not be used.
         *
         * @throws UnsupportedOperationException always
         * @deprecated Constructors don't have a separate return type
         */
        @Deprecated
        @Override
        public ConstructorBuilder craftType(String className) {
            throw new UnsupportedOperationException("Constructors don't have a separate return type. Use fromCraftClass() to set the class.");
        }

        /**
         * Not applicable for constructors as they cannot be static.
         * This method is inherited but should not be used.
         *
         * @throws UnsupportedOperationException always
         * @deprecated Constructors cannot be static
         */
        @Deprecated
        @Override
        public ConstructorBuilder staticAccess() {
            throw new UnsupportedOperationException("Constructors cannot be static.");
        }

        @Override
        protected String getDetailsString() {
            String params = Arrays.stream(paramTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "));
            return "constructor: %s(%s)".formatted(owner != null ? owner.getSimpleName() : "null", params);
        }

        @Override
        public Optional<MethodHandle> find() {
            lastException = null;

            if (owner == null) {
                return Optional.empty();
            }

            String cacheKey = buildCacheKey();
            if (cachingEnabled && cache.containsKey(cacheKey)) {
                return Optional.of((MethodHandle) cache.get(cacheKey));
            }

            try {
                MethodType methodType = MethodType.methodType(void.class, paramTypes);
                MethodHandles.Lookup lookup = isPrivileged
                        ? MethodHandles.privateLookupIn(owner, defaultLookup)
                        : defaultLookup;

                MethodHandle handle = lookup.findConstructor(owner, methodType);

                if (cachingEnabled) {
                    cache.put(cacheKey, handle);
                }
                return Optional.of(handle);
            } catch (ReflectiveOperationException e) {
                lastException = e;
                return Optional.empty();
            }
        }

        private String buildCacheKey() {
            return Arrays.stream(paramTypes)
                    .map(param -> param.getName() + ",")
                    .collect(Collectors.joining("", "constructor:" + owner.getName() + ":", ""));
        }
    }

    /**
     * Enum representing the type of method lookup to perform.
     */
    public enum MethodLookupType {
        /** Virtual method invocation (instance methods) */
        VIRTUAL,
        /** Static method invocation */
        STATIC,
        /** Special method invocation (super methods, private methods) */
        SPECIAL
    }

    /**
     * Enum representing the access type for field handles.
     */
    public enum FieldAccessType {
        /** Read and write access */
        READ_WRITE,
        /** Read-only access */
        READ_ONLY,
        /** Write-only access */
        WRITE_ONLY
    }

    /**
     * Interface for resolving mapped names at runtime.
     */
    public interface MappingResolver {
        /**
         * Resolves the actual field name for the given owner class and field name.
         *
         * @param owner The class containing the field.
         * @param fieldName The original field name.
         * @return The resolved field name.
         */
        String resolveFieldName(Class<?> owner, String fieldName);

        /**
         * Resolves the actual method name for the given owner class and method name.
         *
         * @param owner The class containing the method.
         * @param methodName The original method name.
         * @return The resolved method name.
         */
        String resolveMethodName(Class<?> owner, String methodName);

        /**
         * Resolves a class by its name.
         *
         * @param className The original class name.
         * @return The resolved class.
         * @throws ClassNotFoundException if the class cannot be found.
         */
        Class<?> resolveClass(String className) throws ClassNotFoundException;
    }

    /**
     * Default mapping resolver that returns names unchanged.
     */
    public static class DirectMappingResolver implements MappingResolver {
        @Override
        public String resolveFieldName(Class<?> owner, String fieldName) {
            return fieldName;
        }

        @Override
        public String resolveMethodName(Class<?> owner, String methodName) {
            return methodName;
        }

        @Override
        public Class<?> resolveClass(String className) throws ClassNotFoundException {
            return Class.forName(className);
        }
    }
}