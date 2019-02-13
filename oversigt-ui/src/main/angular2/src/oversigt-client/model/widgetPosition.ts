/**
 * Oversigt API
 * This API provides access to all public operations of Oversigt.
 *
 * OpenAPI spec version: 1.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


export interface WidgetPosition {
    /**
     * The ID of the widget this position applys to
     */
    widgetId: number;
    /**
     * The widget's position on the X axis
     */
    posX: number;
    /**
     * The widget's position on the Y axis
     */
    posY: number;
    /**
     * The widget's width
     */
    sizeX: number;
    /**
     * The widget's height
     */
    sizeY: number;
}
