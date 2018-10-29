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
/* tslint:disable:no-unused-variable member-ordering */

import { Inject, Injectable, Optional }                      from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams,
         HttpResponse, HttpEvent }                           from '@angular/common/http';
import { CustomHttpUrlEncodingCodec }                        from '../encoder';

import { Observable }                                        from 'rxjs/Observable';

import { ErrorResponse } from '../model/errorResponse';
import { LoggerInfo } from '../model/loggerInfo';
import { OversigtEvent } from '../model/oversigtEvent';
import { ThreadInfo } from '../model/threadInfo';

import { BASE_PATH, COLLECTION_FORMATS }                     from '../variables';
import { Configuration }                                     from '../configuration';


@Injectable()
export class SystemService {

    protected basePath = 'http://localhost/api/v1';
    public defaultHeaders = new HttpHeaders();
    public configuration = new Configuration();

    constructor(protected httpClient: HttpClient, @Optional()@Inject(BASE_PATH) basePath: string, @Optional() configuration: Configuration) {
        if (basePath) {
            this.basePath = basePath;
        }
        if (configuration) {
            this.configuration = configuration;
            this.basePath = basePath || configuration.basePath || this.basePath;
        }
    }

    /**
     * @param consumes string[] mime-types
     * @return true: consumes contains 'multipart/form-data', false: otherwise
     */
    private canConsumeForm(consumes: string[]): boolean {
        const form = 'multipart/form-data';
        for (let consume of consumes) {
            if (form === consume) {
                return true;
            }
        }
        return false;
    }


    /**
     * Retrieve the cached events
     * 
     * @param eventSourceId Optional filter to get only one cached event
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public getCachedEvents(eventSourceId?: string, observe?: 'body', reportProgress?: boolean): Observable<Array<OversigtEvent>>;
    public getCachedEvents(eventSourceId?: string, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<OversigtEvent>>>;
    public getCachedEvents(eventSourceId?: string, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<OversigtEvent>>>;
    public getCachedEvents(eventSourceId?: string, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let queryParameters = new HttpParams({encoder: new CustomHttpUrlEncodingCodec()});
        if (eventSourceId !== undefined) {
            queryParameters = queryParameters.set('eventSourceId', <any>eventSourceId);
        }

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<OversigtEvent>>(`${this.basePath}/system/cached-events`,
            {
                params: queryParameters,
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Retreive log file content
     * 
     * @param filename 
     * @param lines Number of lines to read from the log file. Negative value to read from the end of the file.
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public getLogFileContent(filename: string, lines?: number, observe?: 'body', reportProgress?: boolean): Observable<Array<string>>;
    public getLogFileContent(filename: string, lines?: number, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<string>>>;
    public getLogFileContent(filename: string, lines?: number, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<string>>>;
    public getLogFileContent(filename: string, lines?: number, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {
        if (filename === null || filename === undefined) {
            throw new Error('Required parameter filename was null or undefined when calling getLogFileContent.');
        }

        let queryParameters = new HttpParams({encoder: new CustomHttpUrlEncodingCodec()});
        if (lines !== undefined) {
            queryParameters = queryParameters.set('lines', <any>lines);
        }

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<string>>(`${this.basePath}/system/logfiles/${encodeURIComponent(String(filename))}`,
            {
                params: queryParameters,
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Get a list of available log levels
     * 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public getLogLevels(observe?: 'body', reportProgress?: boolean): Observable<Array<string>>;
    public getLogLevels(observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<string>>>;
    public getLogLevels(observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<string>>>;
    public getLogLevels(observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let headers = this.defaultHeaders;

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<string>>(`${this.basePath}/system/log-levels`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Get a list of the server&#39;s loggers
     * 
     * @param configuredLevelsOnly Whether to filter the logger infos
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public getLoggers(configuredLevelsOnly?: boolean, observe?: 'body', reportProgress?: boolean): Observable<Array<LoggerInfo>>;
    public getLoggers(configuredLevelsOnly?: boolean, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<LoggerInfo>>>;
    public getLoggers(configuredLevelsOnly?: boolean, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<LoggerInfo>>>;
    public getLoggers(configuredLevelsOnly?: boolean, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let queryParameters = new HttpParams({encoder: new CustomHttpUrlEncodingCodec()});
        if (configuredLevelsOnly !== undefined) {
            queryParameters = queryParameters.set('configuredLevelsOnly', <any>configuredLevelsOnly);
        }

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<LoggerInfo>>(`${this.basePath}/system/loggers`,
            {
                params: queryParameters,
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Get a list the server&#39;s threads
     * 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public getThreads(observe?: 'body', reportProgress?: boolean): Observable<Array<ThreadInfo>>;
    public getThreads(observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<ThreadInfo>>>;
    public getThreads(observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<ThreadInfo>>>;
    public getThreads(observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<ThreadInfo>>(`${this.basePath}/system/threads`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * List available log files
     * 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public listLogFiles(observe?: 'body', reportProgress?: boolean): Observable<Array<string>>;
    public listLogFiles(observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<Array<string>>>;
    public listLogFiles(observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<Array<string>>>;
    public listLogFiles(observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.get<Array<string>>(`${this.basePath}/system/logfiles`,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Change the log level
     * 
     * @param logger 
     * @param level 
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public setLogLevel(logger: string, level: string, observe?: 'body', reportProgress?: boolean): Observable<any>;
    public setLogLevel(logger: string, level: string, observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<any>>;
    public setLogLevel(logger: string, level: string, observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<any>>;
    public setLogLevel(logger: string, level: string, observe: any = 'body', reportProgress: boolean = false ): Observable<any> {
        if (logger === null || logger === undefined) {
            throw new Error('Required parameter logger was null or undefined when calling setLogLevel.');
        }
        if (level === null || level === undefined) {
            throw new Error('Required parameter level was null or undefined when calling setLogLevel.');
        }

        let queryParameters = new HttpParams({encoder: new CustomHttpUrlEncodingCodec()});
        if (level !== undefined) {
            queryParameters = queryParameters.set('level', <any>level);
        }

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.put<any>(`${this.basePath}/system/loggers/${encodeURIComponent(String(logger))}`,
            null,
            {
                params: queryParameters,
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

    /**
     * Shut down the server
     * Shuts down the dashboard server. Once the request has been accepted the shut down will be initiated after two seconds.
     * @param observe set whether or not to return the data Observable as the body, response or events. defaults to returning the body.
     * @param reportProgress flag to report request and response progress.
     */
    public shutdown(observe?: 'body', reportProgress?: boolean): Observable<any>;
    public shutdown(observe?: 'response', reportProgress?: boolean): Observable<HttpResponse<any>>;
    public shutdown(observe?: 'events', reportProgress?: boolean): Observable<HttpEvent<any>>;
    public shutdown(observe: any = 'body', reportProgress: boolean = false ): Observable<any> {

        let headers = this.defaultHeaders;

        // authentication (JsonWebToken) required
        if (this.configuration.apiKeys["Authorization"]) {
            headers = headers.set('Authorization', this.configuration.apiKeys["Authorization"]);
        }

        // to determine the Accept header
        let httpHeaderAccepts: string[] = [
            'application/json'
        ];
        let httpHeaderAcceptSelected: string | undefined = this.configuration.selectHeaderAccept(httpHeaderAccepts);
        if (httpHeaderAcceptSelected != undefined) {
            headers = headers.set("Accept", httpHeaderAcceptSelected);
        }

        // to determine the Content-Type header
        let consumes: string[] = [
            'application/json'
        ];

        return this.httpClient.post<any>(`${this.basePath}/system/shutdown`,
            null,
            {
                withCredentials: this.configuration.withCredentials,
                headers: headers,
                observe: observe,
                reportProgress: reportProgress
            }
        );
    }

}
