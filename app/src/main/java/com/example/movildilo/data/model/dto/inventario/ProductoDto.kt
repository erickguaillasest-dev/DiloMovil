package com.example.movildilo.data.model.dto.inventario

import com.google.gson.annotations.SerializedName

data class ProductoDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("codigoPrincipal")
    val codigoPrincipal: String? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("marca")
    val marca: String? = null,

    @SerializedName("precioUnitario")
    val precioUnitario: Double? = 0.0,

    @SerializedName("costoPromedioActual", alternate = ["costoPromedio"])
    val costoPromedioActual: Double? = 0.0,

    @SerializedName("categoriaId", alternate = ["categoria_id"])
    val categoriaId: Long? = null,

    @SerializedName("categoria", alternate = ["categoriaNombre"])
    val categoria: String? = null,

    @SerializedName("negocioId", alternate = ["negocio_id"])
    val negocioId: Long? = null,

    @SerializedName("unidadMedida")
    val unidadMedida: String? = null,

    @SerializedName("grabaIva")
    val grabaIva: Boolean? = true,

    @SerializedName("tieneCaducidad")
    val tieneCaducidad: Boolean? = false,

    @SerializedName("imagen")
    val imagen: String? = null
)

data class ProductoResponseDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("codigoPrincipal")
    val codigoPrincipal: String? = null,

    @SerializedName("nombre")
    val nombre: String? = null,

    @SerializedName("marca")
    val marca: String? = null,

    @SerializedName("precioUnitario", alternate = ["pvp", "precio"])
    val precioUnitario: Double? = null,

    @SerializedName("costoPromedioActual", alternate = ["costoPromedio", "costo"])
    val costoPromedio: Double? = null,

    @SerializedName("categoriaId", alternate = ["categoria_id", "idCategoria"])
    val categoriaId: Long? = null,

    @SerializedName("categoria", alternate = ["categoriaNombre", "nombreCategoria"])
    val categoria: String? = null,

    @SerializedName("unidadMedida")
    val unidadMedida: String? = null,

    @SerializedName("grabaIva")
    val grabaIva: Boolean? = true,

    @SerializedName("tieneCaducidad")
    val tieneCaducidad: Boolean? = false,

    @SerializedName("imagen")
    val imagen: String? = null,

    @SerializedName("estado")
    val estado: Boolean? = true,

    @SerializedName("negocioId", alternate = ["negocio_id"])
    val negocioId: Long? = null,

    @SerializedName("fechaCreacion")
    val fechaCreacion: String? = null
)

fun ProductoResponseDto.toProductoDto(): ProductoDto {
    return ProductoDto(
        id = this.id,
        codigoPrincipal = this.codigoPrincipal,
        nombre = this.nombre,
        marca = this.marca,
        precioUnitario = this.precioUnitario,
        costoPromedioActual = this.costoPromedio,
        categoriaId = this.categoriaId,
        categoria = this.categoria,
        unidadMedida = this.unidadMedida,
        grabaIva = this.grabaIva,
        tieneCaducidad = this.tieneCaducidad,
        imagen = this.imagen,
        negocioId = this.negocioId
    )
}

fun List<ProductoResponseDto>.toProductoDtoList(): List<ProductoDto> {
    return this.map { it.toProductoDto() }
}

fun ProductoDto.toResponseDto(negocioId: Long? = null): ProductoResponseDto {
    return ProductoResponseDto(
        id = this.id,
        codigoPrincipal = this.codigoPrincipal,
        nombre = this.nombre,
        marca = this.marca,
        precioUnitario = this.precioUnitario,
        costoPromedio = this.costoPromedioActual,
        categoriaId = this.categoriaId,
        categoria = this.categoria,
        unidadMedida = this.unidadMedida,
        grabaIva = this.grabaIva,
        tieneCaducidad = this.tieneCaducidad,
        imagen = this.imagen,
        estado = true,
        negocioId = negocioId ?: this.negocioId
    )
}