package net.ultranick.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents Minecraft player skin textures (value & signature).
 * Designed for high performance and zero external API dependencies.
 *
 * @author Chatbxn
 */
public final class SkinData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final String value;
    private final String signature;

    public SkinData(@NotNull String name, @NotNull String value, @Nullable String signature) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.signature = signature != null ? signature : "";
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getValue() {
        return value;
    }

    @NotNull
    public String getSignature() {
        return signature;
    }

    public boolean hasSignature() {
        return signature != null && !signature.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkinData skinData = (SkinData) o;
        return Objects.equals(name, skinData.name) &&
                Objects.equals(value, skinData.value) &&
                Objects.equals(signature, skinData.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, signature);
    }

    @Override
    public String toString() {
        return "SkinData{" +
                "name='" + name + '\'' +
                ", valueLength=" + value.length() +
                ", hasSignature=" + hasSignature() +
                '}';
    }
}
