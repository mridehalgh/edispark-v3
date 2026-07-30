import React, {Fragment} from "react";
import {Button} from "@/components/ui/button";

// @ts-ignore
function renderSegment(row, parents = '') {
  const path = `${parents + row.tag}+`;

  return (
      <div className="row">
        <div className="col-12 my-1">
          <Button size="sm"  variant="outline" disabled={true}>
            {row.tag}
          </Button>
          {renderElements(row.elements, path)}
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
            <Fragment>
              +{element.elements && renderCompositeElement(element, parents)}
              {element.value && renderElement(element, parents)}
            </Fragment>
        ))}
      </Fragment>
  );
}

// @ts-ignore
function renderElement(element, parents) {
  const path = `${parents + element.tag}`;

  return (
      <Fragment>
        <Button size="sm" variant="outline" className="outline-secondary border-secondary" disabled={true} data-path-id={path}
                data-path-value={element.value}>
          {element.value || 'empty'}</Button>
        {/*<a*/}
        {/*    className="btn btn-sm btn-outline-danger mt-1"*/}
        {/*    data-path-id={path}*/}
        {/*    data-path-value={element.value}*/}
        {/*>*/}
        {/*  {element.value || 'empty'}*/}
        {/*</a>*/}
      </Fragment>
  );
}

// @ts-ignore
function renderCompositeElement(subElements, parents) {
  const path = `${parents}${subElements.tag}`;

  return (
      <Fragment>
        {subElements.elements.map((element: any) => (
            <Fragment>{renderSubElement(element, path)}:</Fragment>
        ))}
      </Fragment>
  );
}

// @ts-ignore
function renderSubElement(element, parents) {
  const path = `${parents}:${element.tag}`;
  return (
      <Fragment>
        <Button size="sm"  variant="outline" disabled={true}>
          {element.value || 'empty'}
        </Button>
        {/*<a*/}
        {/*    className="btn btn-sm btn-outline-secondary mt-1"*/}
        {/*    data-path-id={path}*/}
        {/*    data-path-value={element.value}*/}
        {/*>*/}
        {/*  {element.value || 'empty'}*/}
        {/*</a>*/}
      </Fragment>
  );
}

// @ts-ignore
function renderSegmentGroup(segmentGroup, parents = '') {
  const path = `${parents}${segmentGroup.tag}\\`;

  return (
      <div className="row">
        <div className="col-12 my-1">
          <div
              className="p-2"
              style={{
                border: '1px dashed #000',
              }}
          >
            {segmentGroup.segments.map((segment: any) => (
                <Fragment>
                  {segment.elements && renderSegment(segment, path)}
                  {segment.segments && renderSegmentGroup(segment, path)}
                </Fragment>
            ))}
          </div>
        </div>
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

export function ParsedDocumentLegacy(document: any) {
  return (
      <div className="text-sm">
        {document.document.functionalGroupList[0].messageList[0].body.map((row: any) => getMappingUi(row))}
        <pre>
        {JSON.stringify(document, null, 2)}
      </pre>
      </div>

  );
}