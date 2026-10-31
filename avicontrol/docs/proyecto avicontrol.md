


# AVICONTROL: Sistema de Gestión y Automatización Avícola

Este sistema busca optimizar la crianza de pollos de engorde mediante el control estricto de galpones, la automatización de la dieta según la edad del lote y el registro de novedades sanitarias para garantizar la rentabilidad en la venta final.

---

### 🐣 Módulo 1: Gestión de Galpones e Infraestructura
Este módulo digitaliza la capacidad física de la granja y organiza la distribución de la población inicial, funcionando como la base de datos central de activos.

#### 1.1. Entidad Galpón (Atributos)
Cada unidad debe estar indexada con metadatos que definan su capacidad operativa:
*   **Identificación:** ID Único (UUID), número o nombre del galpón.
*   **Capacidad:** capacidad de pollos (basado en metros cuadrados y ventilación).
*   **Población Actual:** Cantidad de pollos vivos en el lote actual.
*   **Edad del Lote:** Días transcurridos desde el ingreso (parámetro crítico para la alimentación).

#### 1.2. Ciclo de Vida del Galpón (Estados)
El caso de uso **Actualizar estado** es el único responsable de validar y persistir las transiciones del galpón. Los estados y sus transiciones son:

| Estado actual | Estado destino | Origen autorizado |
|---|---|---|
| Disponible | Productivo | Registro exitoso de lote |
| Disponible | Mantenimiento | Atención de alerta de mantenimiento |
| Productivo | En cosecha | Administrador |
| Productivo | Aislamiento | Alerta sanitaria válida |
| En cosecha | Vaciado sanitario | Alerta de vaciado sanitario válida |
| Aislamiento | Productivo | Alerta sanitaria válida de reanudación |
| Aislamiento | En cosecha | Administrador al resolver el aislamiento y retomar la cosecha |
| Mantenimiento | Disponible | Administrador al finalizar mantenimiento |
| Vaciado sanitario | Disponible | Proceso automático al finalizar el periodo configurado |

Los estados son **Disponible**, **Productivo**, **En cosecha**, **Vaciado sanitario**, **Mantenimiento** y **Aislamiento**. Ningún caso de uso distinto de **Actualizar estado** debe persistir directamente un cambio de estado.

---

### 🧪 Módulo 2: Nutrición, Sanidad y Control de Inventario Vivo
Este módulo es el núcleo operativo que gestiona el crecimiento biológico basándose en reglas de negocio configurables.

#### 2.1. Automatización de Dieta (Parámetros Configurables)
El software emite alertas diarias de despacho de alimento según el rango de días de vida del lote:
*   **Pre-inicio:** Rango configurable (ej. día 1 al 7).
*   **Inicio:** Rango configurable (ej. día 8 al 21).
*   **Broiler (Engorde):** Rango configurable (ej. día 22 hasta el sacrificio).

#### 2.2. Registro Sanitario y Novedades de Inventario
Control de mermas y salud del lote para asegurar la calidad:
*   **Retiros por Mortalidad:** Registro detallado de muertes (causa, fecha y cantidad).
*   **Retiros por Enfermedad:** Aislamiento preventivo para evitar contagios.
*   **Aplicación de Medicamentos:** Registro de tratamientos, dosis y "tiempo de retiro" (días que deben pasar antes del consumo humano).

---

### 💰 Módulo 3: Liquidación de Lote y Análisis de Rentabilidad
Este módulo consolida los datos operativos y de inventario para determinar el éxito financiero de cada galpón.

#### 3.1. Cálculo de Costos de Crianza
Consolidación automática de egresos por lote:
*   **Costo de Alimento:** Kg consumidos de cada tipo multiplicado por su precio de inventario.
*   **Insumos Médicos:** Costo de vacunas y medicamentos aplicados.
*   **Costo de Población:** Valor de adquisición de los pollos iniciales.

#### 3.2. Matriz de Venta Final (Ingreso Neto)

| Concepto | Cálculo Aplicado | Observación |
| :--- | :--- | :--- |
| **Venta Bruta** | Pollos finales x Peso promedio x Precio/kg | Ingreso total de la venta. |
| **Pérdida por Mortalidad** | (Población inicial - Pollos finales) | Porcentaje de merma del lote. |
| **Costos Operativos** | Alimento + Medicina + Indirectos | Egresos totales de producción. |
| **Utilidad Neta** | Venta Bruta - Costos Operativos | Ganancia real por galpón. |

---

**Resultado esperado:** Al finalizar, el software debe permitir configurar los rangos de alimentación (Módulo 2), actualizar el inventario vivo tras reportar muertes (Módulo 1) y generar un reporte de rentabilidad comparando costos vs. ingresos (Módulo 3).
