'use client';
import {
  NavigationMenu,
  NavigationMenuContent,
  NavigationMenuIndicator,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  NavigationMenuTrigger,
  NavigationMenuViewport,
  navigationMenuTriggerStyle,
} from "@/components/ui/navigation-menu"
import React from "react";
import {Link, NavLink} from "react-router-dom";

interface TopNavProps extends React.HTMLAttributes<HTMLElement> {
  links: {
    title: string
    href: string
    isActive: boolean
  }[]
}

export function TopNav({ className, links, ...props }: TopNavProps) {
  return (
      <>
        <NavigationMenu>
          <NavigationMenuList>
                {links.map(({ title, href }) => (
                    <NavLink to={href} key={`${title}-${href}`}>
                      <NavigationMenuItem>
                        <NavigationMenuLink key={`${title}-${href}`} className={navigationMenuTriggerStyle()}>
                          {title}
                        </NavigationMenuLink>
                      </NavigationMenuItem>
                    </NavLink>
                ))}
          </NavigationMenuList>
        </NavigationMenu>
      </>
  )
}
