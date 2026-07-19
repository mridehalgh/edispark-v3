import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {LayoutBody} from "@/components/layout/layout";
import React from "react";
import {ParsedDocument} from "@/app/files/components/parsed";
import {documentData} from "@/app/files/data/document-data";
import {ParsedDocumentLegacy} from "@/app/files/components/parsed-legacy";
import {ParsedRaw} from "@/app/files/components/parsed-raw";
import {ScrollArea} from "@/components/ui/scroll-area";

export function FilesPage() {
  return (
      <LayoutBody className='space-y-4'>
        <div className='flex items-center justify-between space-y-2'>
          <h1 className='text-2xl font-bold tracking-tight md:text-3xl'>
            File
          </h1>
        </div>
        <Tabs
            orientation='vertical'
            defaultValue='overview'
            className='space-y-4'
        >
          <div className='w-full overflow-x-scroll pb-2'>
            <TabsList defaultValue="parsed">
              <TabsTrigger value='mapped'>Mapped</TabsTrigger>
              <TabsTrigger value='parsed'>Parsed</TabsTrigger>
              <TabsTrigger value='raw'>Raw</TabsTrigger>
            </TabsList>
          </div>
          <TabsContent value='mapped' className='space-y-4'>
            Mapped
          </TabsContent>
          <TabsContent value='parsed' className='space-y-4'>
            <div className="w-1/2 h float-left pr-4 pt-4">
              <div className="rounded-md h-full float-left border p-4">
                <ParsedRaw document={documentData} />
              </div>
            </div>
            <div className="w-1/2 h-full h-screen float-left">
            <ScrollArea className="rounded-md h-full float-left rounded-md border p-4">
              <ParsedDocument document={documentData} />
            </ScrollArea>
            </div>
            <div className="w-1/2 float-right p-2">

            </div>
          </TabsContent>
          <TabsContent value='raw' className='space-y-4'>
            Raw
          </TabsContent>
        </Tabs>
      </LayoutBody>
  );
}