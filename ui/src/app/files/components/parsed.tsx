import React, {Fragment} from "react";
import {Button} from "@/components/ui/button";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"
import {Badge} from "@/components/ui/badge";

import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

// @ts-ignore
function renderSegment(row, parents = '') {
  const path = `${parents + row.tag}+`;
  const marginLeft = parents ? "" : "pl-3";

  return (
      <div className="ro mt-2">
        <Accordion type="single"collapsible defaultValue="item-1" className={`bg-white border-accent px-3 border border-solid border-2 ${marginLeft}`}>
          <AccordionItem value="item-1">
            <AccordionTrigger>
              <p className="float-left text-left text-xs"><Badge>{row.tag}</Badge> {row.description}</p>
              <p className="float-right text-right text-xs">Repeats {row.maxRepeat ? row.maxRepeat : "∞" }</p>
            </AccordionTrigger>
            <AccordionContent >
              {renderElements(row.elements, path)}
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>
  );
}

// @ts-ignore
function renderElements(elements, parents) {
  // @ts-ignore
  return (
      <Fragment>
        <Table>
          <TableBody>
            {elements.map((element: any) => (
                <Fragment>
                  <TableRow>
                    {element.elements && renderCompositeElement(element, parents)}
                    {element.value && renderElement(element, parents)}
                  </TableRow>
                </Fragment>
            ))}
          </TableBody>
        </Table>
      </Fragment>
  );
}

// @ts-ignore
function renderElement(element, parents) {
  const path = `${parents + element.tag}`;

  return (
      <Fragment>
        <TableCell className="text-xs" colSpan={1}>{element.tag} - {element.description}</TableCell>
        <TableCell className="text-xs" colSpan={1}>{element.value}</TableCell>
      </Fragment>
  );
}

// @ts-ignore
function renderCompositeElement(subElements, parents) {
  const path = `${parents}${subElements.tag}`;

  return (
      <Fragment>
        <TableCell colSpan={3} className="bg-accent text-xs">
          <Table>
            {subElements.elements.map((element: any) => (
                <TableRow>{renderSubElement(element, path)}</TableRow>
            ))}
          </Table>
        </TableCell>
      </Fragment>
  );
}

// @ts-ignore
function renderSubElement(element, parents) {
  const path = `${parents}:${element.tag}`;
  return (
      <Fragment>
        <TableCell className="text-xs" colSpan={1}>{element.tag} - {element.description}</TableCell>
        <TableCell className="text-xs" colSpan={1}>{element.value}</TableCell>
      </Fragment>
  );
}

// @ts-ignore
function renderSegmentGroup(segmentGroup, parents = '') {
  const path = `${parents}${segmentGroup.tag}\\`;

  const marginLeft = parents ? "bg" : "pl-3";

  return (

      <div className="row mt-3">
        <Accordion type="single"collapsible defaultValue="item-1" className={`bg-accent px-3 border border-solid border-2 ${marginLeft}`}>
          <AccordionItem value="item-1">
            <AccordionTrigger>
              <p className="float-left text-left text-xs"><Badge variant="outline">{segmentGroup.tag} Group</Badge> {segmentGroup.description}</p>
              <p className="float-right text-right text-xs">Repeats {segmentGroup.maxRepeat ? segmentGroup.maxRepeat : "∞" }</p>
              </AccordionTrigger>
            <AccordionContent >
              {segmentGroup.segments.map((segment: any) => (
                  <Fragment>
                    {segment.elements && renderSegment(segment, path)}
                    {segment.segments && renderSegmentGroup(segment, path)}
                  </Fragment>
              ))}
            </AccordionContent>
          </AccordionItem>
        </Accordion>

      </div>
  );
}

function getMappingUi(row: any) {
  return (
      <Fragment>
        {row.elements && renderSegment(row)}
        {row.segments && renderSegmentGroup(row)}
      </Fragment>
  );
}

export function ParsedDocument(document: any) {
  return (
      <div className="text-sm">
        {document.document.functionalGroupList[0].messageList[0].body.map((row: any) => getMappingUi(row))}
      </div>

  );
}