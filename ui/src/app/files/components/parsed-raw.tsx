import React, {Fragment} from "react";
import {Button} from "@/components/ui/button";

// @ts-ignore
function renderSegment(row, parents = '') {
  const path = `${parents + row.tag}+`;

  return (
      <div className="row">
        <div className="col-12 my-1">
          <a className="text-amber-700">
            {row.tag}
          </a>
          {renderElements(row.elements, path)}<a className="text-muted-foreground">&apos;</a>
        </div>
      </div>
  );
}

// @ts-ignore
function renderElements(elements, parents) {
  // @ts-ignore
  return (
      <Fragment>
        {elements.map((element: any) => (
            <a className="text-muted-foreground">
              <a className="text-blue-700">+</a>{element.elements && renderCompositeElement(element, parents)}
              {element.value && renderElement(element, parents)}
            </a>
        ))}
      </Fragment>
  );
}

// @ts-ignore
function renderElement(element, parents) {
  const path = `${parents + element.tag}`;

  return (
      <Fragment>
        <a className="" data-path-id={path}
                data-path-value={element.value}>
          {element.value || ''}</a>
      </Fragment>
  );
}

// @ts-ignore
function renderCompositeElement(subElements, parents) {
  const path = `${parents}${subElements.tag}`;

  return (
      <Fragment>
        {subElements.elements.map((element: any) => (
            <Fragment>{renderSubElement(element, path)}<a className="text-lime-600">:</a></Fragment>
        ))}
      </Fragment>
  );
}

// @ts-ignore
function renderSubElement(element, parents) {
  const path = `${parents}:${element.tag}`;
  return (
      <Fragment>
        <a data-path-id={path} data-path-value={element.value}>
          {element.value || ''}
        </a>
      </Fragment>
  );
}

// @ts-ignore
function renderSegmentGroup(segmentGroup, parents = '') {
  const path = `${parents}${segmentGroup.tag}\\`;

  return (
      <div className="row">
            {segmentGroup.segments.map((segment: any) => (
                <Fragment>
                  {segment.elements && renderSegment(segment, path)}
                  {segment.segments && renderSegmentGroup(segment, path)}
                </Fragment>
            ))}
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

export function ParsedRaw(document: any) {
  return (
      <div className="text-sm">
        <code>
          {document.document.functionalGroupList[0].messageList[0].body.map((row: any) => getMappingUi(row))}
        </code>
      </div>

  );
}